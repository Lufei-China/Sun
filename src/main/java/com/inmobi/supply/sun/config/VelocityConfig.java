package com.inmobi.supply.sun.config;

import com.google.common.io.CharStreams;
import lombok.extern.slf4j.Slf4j;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import org.apache.velocity.runtime.resource.util.StringResourceRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * VelocityConfig
 * <p>
 * author Andrew.Ji
 */
@Configuration
@Slf4j
public class VelocityConfig {
    @Bean
    public VelocityEngine velocityEngine() {
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty("resource.loader", "string");
        velocityEngine.setProperty("string.resource.loader.class",
                "org.apache.velocity.runtime.resource.loader.StringResourceLoader");
        velocityEngine.setProperty("string.resource.loader.repository.class",
                "org.apache.velocity.runtime.resource.util.StringResourceRepositoryImpl");
        velocityEngine.init();

        initWidgets("splash_image_sdk");
        initWidgets("infeed_image_sdk");
        //sdk不支持JsonVideo
        //initWidgets("splash_video_sdk");
        //initWidgets("infeed_video_sdk");
        initWidgets("json_video_sdk");
        initWidgets("banner_image_sdk");
        initWidgets("interstitial_image_sdk");
        initWidgets("banner_int_static_html");
        initWidgets("banner_int_static_html_response");
        initWidgets("interstitial_video_html");
        initWidgets("interstitial_video_html_response");
        initWidgets("interstitial_video_vast_xml");
        initWidgets("banner_h5_html");
        return velocityEngine;
    }

    private void initWidgets(String fileName) {
        StringResourceRepository repository = StringResourceLoader.getRepository();
        String templateStr = null;
        InputStream inputStream = null;
        try {
            inputStream =
                    this.getClass().getClassLoader().getResourceAsStream("template/" + fileName + ".vm");
            templateStr = CharStreams.toString(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error("template widgets loading error:", e);
        }
        repository.putStringResource(fileName, templateStr);
    }
}