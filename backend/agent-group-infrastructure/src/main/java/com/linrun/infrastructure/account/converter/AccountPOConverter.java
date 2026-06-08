package com.linrun.infrastructure.account.converter;

import com.linrun.domain.account.model.ModelUsageRecord;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.model.UserLoginSession;
import com.linrun.domain.account.model.UserMembershipAccount;
import com.linrun.domain.account.model.UserModelConfig;
import com.linrun.domain.account.model.UserQuotaAccount;
import com.linrun.domain.account.model.UserQuotaFlow;
import com.linrun.infrastructure.po.ModelUsageRecordPO;
import com.linrun.infrastructure.po.UserAccountPO;
import com.linrun.infrastructure.po.UserLoginSessionPO;
import com.linrun.infrastructure.po.UserMembershipAccountPO;
import com.linrun.infrastructure.po.UserModelConfigPO;
import com.linrun.infrastructure.po.UserQuotaAccountPO;
import com.linrun.infrastructure.po.UserQuotaFlowPO;
import org.springframework.beans.BeanUtils;

import java.util.List;

public final class AccountPOConverter {

    private AccountPOConverter() {
    }

    public static UserAccountPO toPO(UserAccount entity) {
        if (entity == null) {
            return null;
        }
        UserAccountPO po = new UserAccountPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static UserAccount toEntity(UserAccountPO po) {
        if (po == null) {
            return null;
        }
        UserAccount entity = new UserAccount();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static UserLoginSessionPO toPO(UserLoginSession entity) {
        if (entity == null) {
            return null;
        }
        UserLoginSessionPO po = new UserLoginSessionPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static UserLoginSession toEntity(UserLoginSessionPO po) {
        if (po == null) {
            return null;
        }
        UserLoginSession entity = new UserLoginSession();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static UserQuotaAccount toEntity(UserQuotaAccountPO po) {
        if (po == null) {
            return null;
        }
        UserQuotaAccount entity = new UserQuotaAccount();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static UserQuotaFlowPO toPO(UserQuotaFlow entity) {
        if (entity == null) {
            return null;
        }
        UserQuotaFlowPO po = new UserQuotaFlowPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static UserQuotaFlow toEntity(UserQuotaFlowPO po) {
        if (po == null) {
            return null;
        }
        UserQuotaFlow entity = new UserQuotaFlow();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<UserQuotaFlow> toQuotaFlows(List<UserQuotaFlowPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AccountPOConverter::toEntity).toList();
    }

    public static ModelUsageRecordPO toPO(ModelUsageRecord entity) {
        if (entity == null) {
            return null;
        }
        ModelUsageRecordPO po = new ModelUsageRecordPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static UserMembershipAccount toEntity(UserMembershipAccountPO po) {
        if (po == null) {
            return null;
        }
        UserMembershipAccount entity = new UserMembershipAccount();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static UserMembershipAccountPO toPO(UserMembershipAccount entity) {
        if (entity == null) {
            return null;
        }
        UserMembershipAccountPO po = new UserMembershipAccountPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static UserModelConfig toEntity(UserModelConfigPO po) {
        if (po == null) {
            return null;
        }
        UserModelConfig entity = new UserModelConfig();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static UserModelConfigPO toPO(UserModelConfig entity) {
        if (entity == null) {
            return null;
        }
        UserModelConfigPO po = new UserModelConfigPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }
}
