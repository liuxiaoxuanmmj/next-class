package edu.zzttc.backend.utils;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class FlowUtils {

    @Resource
    StringRedisTemplate template;

    public boolean limitOnceCheck(String key, int blackTime){
        if(Boolean.TRUE.equals(template.hasKey(key))){
            return false;
        }else {
            template.opsForValue().set(key,"", blackTime, TimeUnit.SECONDS);
            return true;
        }
    }
}
