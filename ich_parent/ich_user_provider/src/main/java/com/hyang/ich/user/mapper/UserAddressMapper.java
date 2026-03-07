package com.hyang.ich.user.mapper;

import com.hyang.ich.user.entity.UserAddress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserAddressMapper {

    List<UserAddress> selectByUserId(@Param("userId") Long userId);

    UserAddress selectById(@Param("id") Long id);

    int insert(UserAddress address);

    int updateById(UserAddress address);

    int deleteById(@Param("id") Long id, @Param("userId") Long userId);

    int clearDefault(@Param("userId") Long userId);

    int setDefault(@Param("id") Long id, @Param("userId") Long userId);
}
