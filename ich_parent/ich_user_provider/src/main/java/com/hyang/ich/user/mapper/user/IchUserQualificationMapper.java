package com.hyang.ich.user.mapper.user;

import com.hyang.ich.user.entity.IchUserQualification;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface IchUserQualificationMapper {

    List<IchUserQualification> selectByCondition(@Param("keyword") String keyword,
                                                  @Param("status") Integer status,
                                                  @Param("userId") Long userId,
                                                  @Param("offset") int offset,
                                                  @Param("limit") int limit);

    int countByCondition(@Param("keyword") String keyword,
                          @Param("status") Integer status,
                          @Param("userId") Long userId);

    IchUserQualification selectById(@Param("id") Long id);

    IchUserQualification selectByUserId(@Param("userId") Long userId);

    int insert(IchUserQualification qualification);

    int updateStatus(@Param("id") Long id,
                      @Param("status") Integer status,
                      @Param("rejectReason") String rejectReason,
                      @Param("reviewerId") Long reviewerId);

    int deleteById(@Param("id") Long id);
}
