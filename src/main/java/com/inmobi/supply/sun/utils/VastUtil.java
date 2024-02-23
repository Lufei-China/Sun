package com.inmobi.supply.sun.utils;

import com.inmobi.supply.component.model.adFormat.FormatterModel;
import com.inmobi.supply.sun.model.runtime.RunTimeContext;
import com.inmobi.supply.sun.model.vast.VastXmlInfo;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static cn.inmobi.pb.api.TrackingEvent.*;

@Service
public class VastUtil {

    @Autowired VelocityEngine velocityEngine;

    public String generateVastXmlString(RunTimeContext runTimeContext, FormatterModel model) {

        List<String> startTrackers =
                runTimeContext
                        .getAdRunTimeInfo()
                        .getEventTrackers()
                        .getOrDefault(VIDEO_START, new ArrayList<>());
        List<String> videoQ1Trackers =
                runTimeContext
                        .getAdRunTimeInfo()
                        .getEventTrackers()
                        .getOrDefault(VIDEO_Q1, new ArrayList<>());
        List<String> videoQ2Trackers =
                runTimeContext
                        .getAdRunTimeInfo()
                        .getEventTrackers()
                        .getOrDefault(VIDEO_Q2, new ArrayList<>());
        List<String> videoQ3Trackers =
                runTimeContext
                        .getAdRunTimeInfo()
                        .getEventTrackers()
                        .getOrDefault(VIDEO_Q3, new ArrayList<>());
        List<String> videoCompleteTrackers =
                runTimeContext
                        .getAdRunTimeInfo()
                        .getEventTrackers()
                        .getOrDefault(VIDEO_COMPLETE, new ArrayList<>());
        List<String> clickTrackers =
                runTimeContext.getAdRunTimeInfo().getEventTrackers().getOrDefault(CLICK, new ArrayList<>());

        VastXmlInfo vastXmlInfo =
                VastXmlInfo.builder()
                        .adId(model.getAdId())
                        .adSystem("Inmobi")
                        .title(model.getTitle())
                        .errorUrl("")
                        .impressionUrl(model.getEventTrackers().get(VIEW).get(0))
                        .duration(getDuration(model))//"00:00:20"
                        .start(startTrackers.size() > 0 ? startTrackers.get(0) : null)
                        .q1(videoQ1Trackers.size() > 0 ? videoQ1Trackers.get(0) : null)
                        .mid(videoQ2Trackers.size() > 0 ? videoQ2Trackers.get(0) : null)
                        .q3(videoQ3Trackers.size() > 0 ? videoQ3Trackers.get(0) : null)
                        .complete(videoCompleteTrackers.size() > 0 ? videoCompleteTrackers.get(0) : null)
                        .skip("")
                        .clickThrough(model.getItemUrl())
                        .videoWidth(model.getVideo().getCoverWidth())
                        .videoHeight(model.getVideo().getCoverHeight())
                        .videoUrl(model.getVideo().getVideoUrl())
                        .companionWidth(model.getVideo().getCoverWidth())
                        .companionHeight(model.getVideo().getCoverHeight())
                        .clickTracking(clickTrackers.size() > 0 ? clickTrackers.get(0) : null)
                        // .trackingCreativeView("")
                        .companionClickThrough(model.getItemUrl())
                        .endCardImageUrl(model.getVideo().getCoverUrl())
                        .build();

        Template template = velocityEngine.getTemplate("interstitial_video_vast_xml", "UTF-8");
        VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("vast", vastXmlInfo);
        StringWriter stringWriter = new StringWriter();
        template.merge(velocityContext, stringWriter);
        String vastXmlString = StringUtils.trim(stringWriter.toString());
        return vastXmlString;
    }

    private String getDuration(FormatterModel model) {
        long hours = model.getVideo().getVideoDuration() / 3600;
        long minutes = (model.getVideo().getVideoDuration() % 3600) / 60;
        long seconds = model.getVideo().getVideoDuration() % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}