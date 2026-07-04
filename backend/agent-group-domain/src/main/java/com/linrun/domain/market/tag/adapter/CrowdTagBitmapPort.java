package com.linrun.domain.market.tag.adapter;

import java.util.Optional;

public interface CrowdTagBitmapPort {

    Optional<Long> queryUserNumericId(String userId);

    Optional<Boolean> isUserInTag(String tagId, String userId);

    void markUserInTag(String tagId, long userNumericId);

    int countTaggedUsers(String tagId);

    static CrowdTagBitmapPort noop() {
        return new CrowdTagBitmapPort() {
            @Override
            public Optional<Long> queryUserNumericId(String userId) {
                return Optional.empty();
            }

            @Override
            public Optional<Boolean> isUserInTag(String tagId, String userId) {
                return Optional.empty();
            }

            @Override
            public void markUserInTag(String tagId, long userNumericId) {
            }

            @Override
            public int countTaggedUsers(String tagId) {
                return -1;
            }
        };
    }
}
