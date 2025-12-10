package edu.zzttc.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.zzttc.backend.domain.entity.Account;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountMapper extends BaseMapper<Account> {

}
