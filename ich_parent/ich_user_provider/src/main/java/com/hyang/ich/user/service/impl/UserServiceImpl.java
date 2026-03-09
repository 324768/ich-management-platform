package com.hyang.ich.user.service.impl;

import com.hyang.ich.common.enums.ResultCode;
import com.hyang.ich.common.exception.BusinessException;
import com.hyang.ich.common.utils.JwtUtils;
import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.user.UserService;
import com.hyang.ich.user.dto.IchUserQualificationDTO;
import com.hyang.ich.user.dto.UserAddressDTO;
import com.hyang.ich.user.dto.UserDTO;
import com.hyang.ich.user.dto.UserLoginDTO;
import com.hyang.ich.user.dto.UserRegisterDTO;
import com.hyang.ich.user.entity.IchUserQualification;
import com.hyang.ich.user.entity.User;
import com.hyang.ich.user.entity.UserAddress;
import com.hyang.ich.user.mapper.user.IchUserQualificationMapper;
import com.hyang.ich.user.mapper.user.UserAddressMapper;
import com.hyang.ich.user.mapper.user.UserMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@DubboService
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserAddressMapper userAddressMapper;

    @Autowired
    private IchUserQualificationMapper qualificationMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========== 用户认证 ==========

    @Override
    public UserDTO register(UserRegisterDTO registerDTO) {
        User existing = userMapper.selectByUsername(registerDTO.getUsername());
        if (existing != null) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS);
        }

        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setNickname(registerDTO.getNickname());
        user.setEmail(registerDTO.getEmail());
        user.setPhone(registerDTO.getPhone());
        user.setStatus(1);

        userMapper.insert(user);
        log.info("用户注册成功: {}", user.getUsername());
        return toUserDTO(user);
    }

    @Override
    public String login(UserLoginDTO loginDTO) {
        User user = userMapper.selectByUsername(loginDTO.getUsername());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }

        user.setLastLoginTime(new Date());
        userMapper.updateById(user);
        userMapper.updateOnlineStatus(user.getId(), 1);

        String token = JwtUtils.generateToken(user.getId(), user.getUsername());
        log.info("用户登录成功: {}", user.getUsername());
        return token;
    }

    // ========== 用户信息 ==========

    @Override
    public UserDTO findById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return toUserDTO(user);
    }

    @Override
    public UserDTO findByUsername(String username) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return toUserDTO(user);
    }

    @Override
    public void updateUser(UserDTO userDTO) {
        User user = new User();
        user.setId(userDTO.getId());
        user.setNickname(userDTO.getNickname());
        user.setAvatar(userDTO.getAvatar());
        user.setEmail(userDTO.getEmail());
        user.setPhone(userDTO.getPhone());
        userMapper.updateById(user);
    }

    @Override
    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR, "原密码错误");
        }
        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
    }

    @Override
    public PageResult<UserDTO> listUsers(int pageNum, int pageSize, String keyword) {
        int offset = (pageNum - 1) * pageSize;
        List<User> users = userMapper.selectList(keyword, offset, pageSize);
        long total = userMapper.countList(keyword);

        List<UserDTO> dtoList = new ArrayList<>();
        for (User u : users) {
            dtoList.add(toUserDTO(u));
        }
        return PageResult.of(pageNum, pageSize, total, dtoList);
    }

    @Override
    public void updateUserStatus(Long userId, Integer status) {
        userMapper.updateStatus(userId, status);
    }

    // ========== 收货地址 ==========

    @Override
    public List<UserAddressDTO> listAddresses(Long userId) {
        List<UserAddress> addresses = userAddressMapper.selectByUserId(userId);
        List<UserAddressDTO> dtoList = new ArrayList<>();
        for (UserAddress addr : addresses) {
            dtoList.add(toAddressDTO(addr));
        }
        return dtoList;
    }

    @Override
    public UserAddressDTO addAddress(UserAddressDTO addressDTO) {
        UserAddress address = new UserAddress();
        address.setUserId(addressDTO.getUserId());
        address.setReceiverName(addressDTO.getReceiverName());
        address.setReceiverPhone(addressDTO.getReceiverPhone());
        address.setProvince(addressDTO.getProvince());
        address.setCity(addressDTO.getCity());
        address.setDistrict(addressDTO.getDistrict());
        address.setDetailAddress(addressDTO.getDetailAddress());
        address.setIsDefault(addressDTO.getIsDefault() != null ? addressDTO.getIsDefault() : 0);
        userAddressMapper.insert(address);
        return toAddressDTO(address);
    }

    @Override
    public void updateAddress(UserAddressDTO addressDTO) {
        UserAddress address = new UserAddress();
        address.setId(addressDTO.getId());
        address.setReceiverName(addressDTO.getReceiverName());
        address.setReceiverPhone(addressDTO.getReceiverPhone());
        address.setProvince(addressDTO.getProvince());
        address.setCity(addressDTO.getCity());
        address.setDistrict(addressDTO.getDistrict());
        address.setDetailAddress(addressDTO.getDetailAddress());
        userAddressMapper.updateById(address);
    }

    @Override
    public void deleteAddress(Long userId, Long addressId) {
        userAddressMapper.deleteById(addressId, userId);
    }

    @Override
    public void setDefaultAddress(Long userId, Long addressId) {
        userAddressMapper.clearDefault(userId);
        userAddressMapper.setDefault(addressId, userId);
    }

    @Override
    public UserDTO findByNickname(String nickname) {
        User user = userMapper.selectByNickname(nickname);
        if (user == null) return null;
        return toUserDTO(user);
    }

    @Override
    public void deleteUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        userMapper.deleteById(userId);
        log.info("用户已删除(Ultra): userId={}", userId);
    }

    @Override
    public List<Long> listOnlineUserIds() {
        return userMapper.selectRecentLoginUserIds(30);
    }

    @Override
    public List<UserDTO> listOnlineUsers() {
        List<User> users = userMapper.selectOnlineUsers();
        List<UserDTO> dtoList = new ArrayList<>();
        for (User u : users) {
            dtoList.add(toUserDTO(u));
        }
        return dtoList;
    }

    @Override
    public void setOnlineStatus(Long userId, boolean online) {
        userMapper.updateOnlineStatus(userId, online ? 1 : 0);
        log.debug("用户在线状态更新: userId={}, online={}", userId, online);
    }

    @Override
    public int clearInactiveUsers(int timeoutMinutes) {
        int count = userMapper.clearInactiveUsers(timeoutMinutes);
        if (count > 0) {
            log.info("清理不活跃用户在线状态: {} 人 (超时{}分钟)", count, timeoutMinutes);
        }
        return count;
    }

    @Override
    public long countUsers() {
        return userMapper.countAll();
    }

    // ========== 用户资格认证 ==========

    @Override
    public IchUserQualificationDTO submitQualification(IchUserQualificationDTO dto) {
        IchUserQualification entity = new IchUserQualification();
        entity.setUserId(dto.getUserId());
        entity.setUserName(dto.getUserName());
        entity.setUserPhone(dto.getUserPhone());
        entity.setQualificationType(dto.getQualificationType());
        entity.setTitle(dto.getTitle());
        entity.setDescription(dto.getDescription());
        entity.setMaterials(toJsonString(dto.getMaterials()));
        entity.setIdCardFront(dto.getIdCardFront());
        entity.setIdCardBack(dto.getIdCardBack());
        entity.setCertificateImages(toJsonString(dto.getCertificateImages()));
        entity.setStatus(0);
        qualificationMapper.insert(entity);
        dto.setId(entity.getId());
        dto.setStatus(0);
        return dto;
    }

    @Override
    public IchUserQualificationDTO getQualificationByUserId(Long userId) {
        IchUserQualification entity = qualificationMapper.selectByUserId(userId);
        return entity != null ? toQualificationDTO(entity) : null;
    }

    @Override
    public PageResult<IchUserQualificationDTO> listQualifications(int pageNum, int pageSize, String keyword, Integer status) {
        int offset = (pageNum - 1) * pageSize;
        List<IchUserQualification> list = qualificationMapper.selectByCondition(keyword, status, null, offset, pageSize);
        int total = qualificationMapper.countByCondition(keyword, status, null);
        List<IchUserQualificationDTO> dtoList = list.stream().map(this::toQualificationDTO).collect(Collectors.toList());
        return new PageResult<>(pageNum, pageSize, (long) total, dtoList);
    }

    @Override
    public void reviewQualification(Long id, Integer status, String rejectReason, Long reviewerId) {
        qualificationMapper.updateStatus(id, status, rejectReason, reviewerId);
        // 审核通过时，更新用户的 heritage_flag
        if (status != null && status == 1) {
            IchUserQualification q = qualificationMapper.selectById(id);
            if (q != null) {
                userMapper.updateHeritageFlag(q.getUserId(), 1);
            }
        } else if (status != null && status == 2) {
            IchUserQualification q = qualificationMapper.selectById(id);
            if (q != null) {
                userMapper.updateHeritageFlag(q.getUserId(), 0);
            }
        }
    }

    @Override
    public boolean hasHeritageFlag(Long userId) {
        User user = userMapper.selectById(userId);
        return user != null && user.getHeritageFlag() != null && user.getHeritageFlag() == 1;
    }

    // ========== 实体转DTO ==========

    private UserDTO toUserDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setAvatar(user.getAvatar());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setStatus(user.getStatus());
        dto.setHeritageFlag(user.getHeritageFlag());
        dto.setLastLoginTime(user.getLastLoginTime());
        dto.setLastLoginIp(user.getLastLoginIp());
        dto.setIsOnline(user.getIsOnline());
        dto.setLastActiveTime(user.getLastActiveTime());
        dto.setCreateTime(user.getCreateTime());
        dto.setUpdateTime(user.getUpdateTime());
        return dto;
    }

    private IchUserQualificationDTO toQualificationDTO(IchUserQualification entity) {
        IchUserQualificationDTO dto = new IchUserQualificationDTO();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setUserName(entity.getUserName());
        dto.setUserPhone(entity.getUserPhone());
        dto.setQualificationType(entity.getQualificationType());
        dto.setTitle(entity.getTitle());
        dto.setDescription(entity.getDescription());
        dto.setMaterials(parseJsonList(entity.getMaterials()));
        dto.setIdCardFront(entity.getIdCardFront());
        dto.setIdCardBack(entity.getIdCardBack());
        dto.setCertificateImages(parseJsonList(entity.getCertificateImages()));
        dto.setStatus(entity.getStatus());
        dto.setRejectReason(entity.getRejectReason());
        dto.setReviewerId(entity.getReviewerId());
        dto.setReviewTime(entity.getReviewTime());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }

    private String toJsonString(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try { return objectMapper.writeValueAsString(list); } catch (Exception e) { return null; }
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try { return objectMapper.readValue(json, new TypeReference<List<String>>() {}); } catch (Exception e) { return Collections.emptyList(); }
    }

    private UserAddressDTO toAddressDTO(UserAddress addr) {
        UserAddressDTO dto = new UserAddressDTO();
        dto.setId(addr.getId());
        dto.setUserId(addr.getUserId());
        dto.setReceiverName(addr.getReceiverName());
        dto.setReceiverPhone(addr.getReceiverPhone());
        dto.setProvince(addr.getProvince());
        dto.setCity(addr.getCity());
        dto.setDistrict(addr.getDistrict());
        dto.setDetailAddress(addr.getDetailAddress());
        dto.setIsDefault(addr.getIsDefault());
        dto.setCreateTime(addr.getCreateTime());
        dto.setUpdateTime(addr.getUpdateTime());
        return dto;
    }
}


