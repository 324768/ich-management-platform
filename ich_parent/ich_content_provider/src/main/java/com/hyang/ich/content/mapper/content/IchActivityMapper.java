package com.hyang.ich.content.mapper.content;

import com.hyang.ich.content.entity.IchActivity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface IchActivityMapper {

    List<IchActivity> selectByCondition(@Param("keyword") String keyword,
                                        @Param("status") Integer status,
                                        @Param("activityType") Integer activityType,
                                        @Param("approvalStatus") Integer approvalStatus,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    int countByCondition(@Param("keyword") String keyword,
                         @Param("status") Integer status,
                         @Param("activityType") Integer activityType,
                         @Param("approvalStatus") Integer approvalStatus);

    IchActivity selectById(@Param("id") Long id);

    int insert(IchActivity activity);

    int update(IchActivity activity);

    int deleteById(@Param("id") Long id);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int incrementParticipants(@Param("id") Long id);

    int updateApprovalStatus(@Param("id") Long id, @Param("approvalStatus") Integer approvalStatus,
                              @Param("rejectReason") String rejectReason, @Param("reviewerId") Long reviewerId);

    List<IchActivity> selectByApprovalStatus(@Param("approvalStatus") Integer approvalStatus,
                                              @Param("keyword") String keyword,
                                              @Param("offset") int offset, @Param("limit") int limit);

    int countByApprovalStatus(@Param("approvalStatus") Integer approvalStatus, @Param("keyword") String keyword);
}
