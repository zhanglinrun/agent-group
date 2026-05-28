package com.linrun.infrastructure.converter;

import com.linrun.domain.agent.conversation.model.GuideConversationMessage;
import com.linrun.infrastructure.po.GuideConversationMessagePO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentPOConverterTest {

    @Test
    void shouldUseEmptyImageUrlWhenMessageHasNoImage() {
        GuideConversationMessagePO po = AgentPOConverter.toPO(
                GuideConversationMessage.user("only text", null));

        assertEquals("", po.getImageUrl());
    }
}
