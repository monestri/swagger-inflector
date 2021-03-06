/*
 *  Copyright 2015 SmartBear Software
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.swagger.inflector;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.fasterxml.jackson.jaxrs.xml.JacksonJaxbXMLProvider;

import io.swagger.inflector.config.Configuration;
import io.swagger.inflector.processors.ExampleSerializer;
import io.swagger.inflector.processors.JsonExampleSerializer;
import io.swagger.jaxrs.listing.SwaggerSerializers;
import io.swagger.models.Model;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import io.swagger.util.Json;
import io.swagger.util.Yaml;

import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerConfig;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import java.util.Map;

public class SwaggerInflector extends ResourceConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(SwaggerInflector.class);
    private Configuration config;
    private String basePath;
    private String originalBasePath;
    private ServletContext servletContext;

    public SwaggerInflector(Configuration configuration) {
      init(configuration);
    }

    public SwaggerInflector(@Context ServletContext servletContext) {
        this.servletContext = servletContext;
        Configuration config = null;
        if(servletContext != null) {
          if(servletContext.getInitParameter("inflector-config") != null) {
            try {
              config = Configuration.read(servletContext.getInitParameter("inflector-config"));
            } catch (Exception e) {
              LOGGER.error("unable to read configuration from init param");
            }
          }
        }
        if(config == null) {
          // use default location
          config = Configuration.read();
        }
        init(config);
    }
    
    protected void init(Configuration configuration) {
      config = configuration;
      Swagger swagger = new SwaggerParser().read(config.getSwaggerUrl(), null, true);

      if (swagger != null) {
          originalBasePath = swagger.getBasePath();
          StringBuilder b = new StringBuilder();

          if(!"".equals(configuration.getRootPath()))
            b.append(configuration.getRootPath());
          if(swagger.getBasePath() != null) {
            b.append(swagger.getBasePath());
          }
          if(b.length() > 0) {
            swagger.setBasePath(b.toString());
          }

          Map<String, Path> paths = swagger.getPaths();
          Map<String, Model> definitions = swagger.getDefinitions();
          for (String pathString : paths.keySet()) {
              Path path = paths.get(pathString);
              final Resource.Builder builder = Resource.builder();
              this.basePath = configuration.getRootPath() + swagger.getBasePath();

              builder.path(basePath(originalBasePath, pathString));
              Operation operation;

              operation = path.getGet();
              if (operation != null) {
                  addOperation(pathString, builder, HttpMethod.GET, operation, definitions);
              }
              operation = path.getPost();
              if (operation != null) {
                  addOperation(pathString, builder, HttpMethod.POST, operation, definitions);
              }
              operation = path.getPut();
              if (operation != null) {
                  addOperation(pathString, builder, HttpMethod.PUT, operation, definitions);
              }
              operation = path.getDelete();
              if (operation != null) {
                  addOperation(pathString, builder, HttpMethod.DELETE, operation, definitions);
              }
              operation = path.getOptions();
              if (operation != null) {
                  addOperation(pathString, builder, HttpMethod.OPTIONS, operation, definitions);
              }
              operation = path.getPatch();
              if (operation != null) {
                  addOperation(pathString, builder, "PATCH", operation, definitions);
              }
              registerResources(builder.build());
          }

          // enable swagger JSON
          enableSwaggerJSON(swagger);

          // enable swagger YAML
          enableSwaggerYAML(swagger);
      } else {
          LOGGER.error("No swagger definition detected!  Not much to do...");
      }
      // JSON
      register(JacksonJsonProvider.class);

      // XML
      register(JacksonJaxbXMLProvider.class);

      register(new MultiPartFeature());

      // Swagger serializers
      register(SwaggerSerializers.class);

      // XML mapper
      SimpleModule simpleModule = new SimpleModule();
      simpleModule.addSerializer(new JsonExampleSerializer());
      Json.mapper().registerModule(simpleModule);
      Yaml.mapper().registerModule(simpleModule);

      // Example serializer
      register(ExampleSerializer.class);      
    }

    private String basePath(String basePath, String path) {
        if (StringUtils.isEmpty(basePath)) {
            return path;
        }
        return basePath + path;
    }

    private void enableSwaggerJSON(Swagger swagger) {
        final Resource.Builder builder = Resource.builder();
        builder.path(basePath(originalBasePath, "/swagger.json"))
            .addMethod(HttpMethod.GET)
            .produces(MediaType.APPLICATION_JSON)
            .handledBy(new SwaggerResourceController(swagger))
            .build();

        registerResources(builder.build());
    }

    private void enableSwaggerYAML(Swagger swagger) {
        final Resource.Builder builder = Resource.builder();
        builder.path(basePath(originalBasePath, "/swagger.yaml"))
            .addMethod(HttpMethod.GET)
            .produces("application/yaml")
            .handledBy(new SwaggerResourceController(swagger))
            .build();

        registerResources(builder.build());
    }

    private void addOperation(String pathString, Resource.Builder builder, String method, Operation operation, Map<String, Model> definitions) {
        // TODO: handle other content types
        LOGGER.debug("adding operation `" + pathString + "` " + method);
        builder.addMethod(method).handledBy(new SwaggerOperationController(config, pathString, method, operation, definitions));
    }
}
