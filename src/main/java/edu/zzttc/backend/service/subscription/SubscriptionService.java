package edu.zzttc.backend.service.subscription;

import edu.zzttc.backend.domain.entity.SubscriptionPreference;

public interface SubscriptionService {
    SubscriptionPreference get(Integer userId);
    void subscribe(Integer userId, SubscriptionPreference pref);
    void unsubscribe(Integer userId);
}

