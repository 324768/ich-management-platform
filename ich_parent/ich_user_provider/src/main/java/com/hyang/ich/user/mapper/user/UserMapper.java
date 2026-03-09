package com.hyang.ich.user.mapper.user;

import com.hyang.ich.user.entity.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper {

    User selectById(@Param("id") Long id);

    User selectByUsername(@Param("username") String username);

    int insert(User user);

    int updateById(User user);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int updatePassword(@Param("id") Long id, @Param("password") String password);

    List<User> selectList(@Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);

    long countList(@Param("keyword") String keyword);

    long countAll();

    int updateHeritageFlag(@Param("id") Long id, @Param("heritageFlag") Integer heritageFlag);

    User selectByNickname(@Param("nickname") String nickname);

    int deleteById(@Param("id") Long id);

    List<Long> selectRecentLoginUserIds(@Param("minutes") int minutes);

    int updateOnlineStatus(@Param("id") Long id, @Param("isOnline") Integer isOnline);

    List<User> selectOnlineUsers();

    int clearInactiveUsers(@Param("timeoutMinutes") int timeoutMinutes);
}
