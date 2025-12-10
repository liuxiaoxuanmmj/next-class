package edu.zzttc.backend.service.subscription.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.zzttc.backend.domain.entity.SubscriptionPreference;
import edu.zzttc.backend.mapper.SubscriptionPreferenceMapper;
import edu.zzttc.backend.service.subscription.SubscriptionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionServiceImpl implements SubscriptionService {
    @Resource
    private SubscriptionPreferenceMapper mapper;

    @Override
    public SubscriptionPreference get(Integer userId) {
        return mapper.selectOne(Wrappers.<SubscriptionPreference>lambdaQuery().eq(SubscriptionPreference::getUserId, userId));
    }

    @Override
    public void subscribe(Integer userId, SubscriptionPreference pref) {
        SubscriptionPreference old = get(userId);
        if (old == null) {
            pref.setUserId(userId);
            pref.setSubscribed(Boolean.TRUE);
            mapper.insert(pref);
        } else {
            old.setSubscribed(Boolean.TRUE);
            old.setTimezone(pref.getTimezone());
            old.setDailyTime(pref.getDailyTime());
            old.setChannels(pref.getChannels());
            old.setAdvanceMinutes(pref.getAdvanceMinutes());
            mapper.updateById(old);
        }
    }

    @Override
    public void unsubscribe(Integer userId) {
        SubscriptionPreference old = get(userId);
        if (old != null) {
            old.setSubscribed(Boolean.FALSE);
            mapper.updateById(old);
        }
    }
}

