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

package io.swagger.inflector.config;

import io.swagger.util.Yaml;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Configuration {
    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);

    private final Map<String, Class<?>> modelMap = new HashMap<String, Class<?>>();
    private String controllerPackage;
    private String modelPackage;
    private String swaggerUrl;
    private int invalidRequestCode = 400;
    private String rootPath = "";

    public static Configuration read() {
    	String swaggerUrl = System.getProperty("swaggerUrl", "swagger.yaml");
        String configLocation = System.getProperty("config", "inflector.yaml");
        System.out.println("loading config from " + configLocation);
        System.out.println("loading swaggerUrl from " + swaggerUrl);
        if(configLocation != null) {
          try {
            output = read(configLocation);
          }catch (Exception e) {
            // continue
            LOGGER.warn("couldn't read inflector config from system property");
          }
        }
        
        if (output == null){
	        try {
	            // try to load from resources
	            
	            InputStream is = Configuration.class.getClassLoader().getResourceAsStream("/WEB-INF/inflector.yaml");
	            if(is != null) {
	                try {
	                  output =  Yaml.mapper().readValue(is, Configuration.class);
	                } catch (Exception e) {
	                  LOGGER.warn("couldn't read inflector config from resource stream");
	                  // continue
	                }
	            }
	        } catch (Exception e) {
	          LOGGER.warn("Returning default configuration!");
	        }
	        
	        if (output == null)
	        	output = defaultConfiguration();
        }
        
        output.setSwaggerUrl(swaggerUrl);
        return output;
    }

    public static Configuration read(String configLocation) throws Exception {
        return Yaml.mapper().readValue(new File(configLocation), Configuration.class);
    }

    public static Configuration defaultConfiguration() {
        return new Configuration()
            .controllerPackage("io.swagger.sample.controllers")
            .modelPackage("io.swagger.sample.models")
            .swaggerUrl("swagger.yaml");
    }

    public Configuration modelPackage(String modelPackage) {
        this.modelPackage = modelPackage;
        return this;
    }

    public Configuration controllerPackage(String controllerPackage) {
        this.controllerPackage = controllerPackage;
        return this;
    }

    public Configuration modelMap(String name, Class<?> cls) {
        modelMap.put(name, cls);
        return this;
    }

    public Configuration swaggerUrl(String swaggerUrl) {
        this.swaggerUrl = swaggerUrl;
        return this;
    }

    public Configuration() {
    }

    public void setControllerPackage(String controllerPackage) {
        this.controllerPackage = controllerPackage;
    }

    public String getControllerPackage() {
        return controllerPackage;
    }

    public void setModelPackage(String modelPackage) {
        this.modelPackage = modelPackage;
    }

    public String getModelPackage() {
        return modelPackage;
    }

    public void setModelMappings(Map<String, String> mappings) {
        for (String key : mappings.keySet()) {
            String className = mappings.get(key);
            Class<?> cls;
            try {
                ClassLoader classLoader = Configuration.class.getClassLoader();
                cls = classLoader.loadClass(className);
                modelMap.put(key, cls);
            } catch (ClassNotFoundException e) {
                LOGGER.error("unable to add mapping for `" + key + "` : `" + className + "`, " + e.getMessage());
            }
        }
    }

    public Map<String, String> getModelMappings() {
        Map<String, String> output = new HashMap<String, String>();
        for (String key : modelMap.keySet()) {
            Class<?> value = modelMap.get(key);
            output.put(key, value.getCanonicalName());
        }
        return output;
    }

    @JsonIgnore
    public void addModelMapping(String name, Class<?> cls) {
        modelMap.put(name, cls);
    }
    public Class<?> getModelMapping(String name) {
        return modelMap.get(name);
    }

    public String getSwaggerUrl() {
        return swaggerUrl;
    }
    public void setSwaggerUrl(String swaggerUrl) {
        this.swaggerUrl = swaggerUrl;
    }

    public void setInvalidRequestStatusCode(int code) {
        this.invalidRequestCode = code;
    }
    public int getInvalidRequestStatusCode() {
        return invalidRequestCode;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }
    public String getRootPath() {
        return rootPath;
    }

}