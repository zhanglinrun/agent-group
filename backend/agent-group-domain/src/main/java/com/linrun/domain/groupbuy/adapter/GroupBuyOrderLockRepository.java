package com.linrun.domain.groupbuy.adapter;

import com.linrun.domain.groupbuy.model.GroupBuyLockResult;
import com.linrun.domain.groupbuy.model.GroupBuyOrderLock;
import com.linrun.domain.groupbuy.model.GroupBuySettlementResult;
import com.linrun.domain.groupbuy.model.GroupBuyTeam;

import java.util.List;
import java.util.Optional;

public interface GroupBuyOrderLockRepository {

    Optional<GroupBuyOrderLock> queryLockByIdempotentKey(String idempotentKey);

    Optional<GroupBuyTeam> queryTeamByTeamId(String teamId);

    GroupBuyLockResult lockNewTeam(GroupBuyTeam team, GroupBuyOrderLock orderLock);

    GroupBuyLockResult lockExistingTeam(GroupBuyOrderLock orderLock);

    Optional<GroupBuyOrderLock> queryLockByOrderId(String orderId);

    GroupBuySettlementResult settlePaidOrder(String orderId);

    List<String> queryPaidOrderIdsByTeamId(String teamId);

    GroupBuySettlementResult releaseLockedOrder(String orderId);

    GroupBuySettlementResult releasePaidOrder(String orderId);
}
