package com.inmobi.supply.sun.envelop.test;

import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.service.postProc.format.module.AdFormatterWorker;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;


@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FormatterTest {

    @Autowired
    private AdFormatterWorker adFormatterWorker;

    @Test
    public void testVelocity() {
        RunTimeContext context = new RunTimeContext();
        /*try {
            adFormatterWorker.work(context);
        } catch (SunException e) {
            e.printStackTrace();
        }*/

    }
}
