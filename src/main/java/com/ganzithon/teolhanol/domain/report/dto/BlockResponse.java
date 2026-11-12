package com.ganzithon.teolhanol.domain.report.dto;

import com.ganzithon.teolhanol.domain.report.entity.Block;
import java.time.LocalDateTime;

/** 차단한 목동. 상대의 user_id·이메일은 담지 않고 닉네임만 내보냅니다. */
public record BlockResponse(Long blockId, String nickname, LocalDateTime blockedAt) {

    public static BlockResponse from(Block block) {
        return new BlockResponse(block.getId(), block.getBlocked().getNickname(), block.getCreatedAt());
    }
}
