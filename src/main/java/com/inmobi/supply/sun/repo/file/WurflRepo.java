//package com.inmobi.supply.sun.repo.file;
//
//import com.scientiamobile.wurfl.core.GeneralWURFLEngine;
//import lombok.extern.slf4j.Slf4j;
//
//import java.io.IOException;
//
///**
// * ipdb加载
// *
// * @author remi.li
// */
//@Slf4j
//public class WurflRepo {
//    private static volatile GeneralWURFLEngine capabilitiesEngine = null;
//
//    public static void init() throws Exception {
//        try {
//            capabilitiesEngine = new GeneralWURFLEngine("/opt/sun/wurfl.zip");
//            capabilitiesEngine.load();
//        } catch (Exception e) {
//            log.error("WurflRepo", e);
//        }
//    }
//
//    public static GeneralWURFLEngine getGeneralWURFLEngine() {
//        if (capabilitiesEngine == null) {
//            try {
//                log.info("WurflRepo is null");
//                init();
//            } catch (Exception e) {
//            }
//        }
//        return capabilitiesEngine;
//    }
//}
