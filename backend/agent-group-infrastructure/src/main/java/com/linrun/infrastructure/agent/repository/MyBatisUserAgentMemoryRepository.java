package com.linrun.infrastructure.agent.repository;

import com.linrun.domain.agent.memory.adapter.UserAgentMemoryRepository;
import com.linrun.domain.agent.memory.model.UserAgentMemory;
import com.linrun.infrastructure.dao.IUserAgentMemoryDao;
import com.linrun.infrastructure.po.UserAgentMemoryPO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisUserAgentMemoryRepository implements UserAgentMemoryRepository {

    private final IUserAgentMemoryDao memoryDao;

    public MyBatisUserAgentMemoryRepository(IUserAgentMemoryDao memoryDao) {
        this.memoryDao = memoryDao;
    }

    @Override
    public List<UserAgentMemory> queryByUser(String userId, boolean enabledOnly, int limit) {
        List<UserAgentMemoryPO> memories = memoryDao.queryByUser(userId, enabledOnly, limit);
        return memories == null ? List.of() : memories.stream().map(this::toEntity).toList();
    }

    @Override
    public Optional<UserAgentMemory> queryByType(String userId, String memoryType) {
        return Optional.ofNullable(toEntity(memoryDao.queryByType(userId, memoryType)));
    }

    @Override
    public void upsert(UserAgentMemory memory) {
        memoryDao.upsert(toPO(memory));
    }

    @Override
    public int disable(String userId, String memoryType) {
        return memoryDao.disable(userId, memoryType);
    }

    @Override
    public int delete(String userId, String memoryType) {
        return memoryDao.delete(userId, memoryType);
    }

    private UserAgentMemoryPO toPO(UserAgentMemory entity) {
        if (entity == null) {
            return null;
        }
        UserAgentMemoryPO po = new UserAgentMemoryPO();
        BeanUtils.copyProperties(entity, po);
        po.setEnabled(entity.getEnabled() == null || entity.getEnabled());
        return po;
    }

    private UserAgentMemory toEntity(UserAgentMemoryPO po) {
        if (po == null) {
            return null;
        }
        UserAgentMemory entity = new UserAgentMemory();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }
}
