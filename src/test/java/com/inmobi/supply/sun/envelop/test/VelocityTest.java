package com.inmobi.supply.sun.envelop.test;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import org.apache.velocity.runtime.resource.util.StringResourceRepository;
import org.junit.Test;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public class VelocityTest {

    @Test
    public void testVelocity() {
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty("resource.loader", "string");
        velocityEngine.setProperty("string.resource.loader.class",
                "org.apache.velocity.runtime.resource.loader.StringResourceLoader");
        velocityEngine.setProperty("string.resource.loader.repository.class",
                "org.apache.velocity.runtime.resource.util.StringResourceRepositoryImpl");
        velocityEngine.init();

        StringResourceRepository repository = StringResourceLoader.getRepository();
        String templateStr = "#foreach($element in $list)\n" +
                " This is $element\n" +
                "\n" +
                "#end\n" +
                "$name";
        repository.putStringResource("name", templateStr);
        final Template template = velocityEngine.getTemplate("name", "UTF-8");
        final StringWriter sw = new StringWriter();

        // 设置变量，velocityContext是一个类似map的结构
        VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("name", "world");
        List<String> list = new ArrayList<String>();
        list.add("jack");
        list.add("kitty");
        velocityContext.put("list", list);

        // 输出渲染后的结果
        StringWriter stringWriter = new StringWriter();
        template.merge(velocityContext, stringWriter);

        System.out.println(stringWriter.toString());

    }
}
