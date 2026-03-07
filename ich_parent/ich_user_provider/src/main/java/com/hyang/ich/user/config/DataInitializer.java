package com.hyang.ich.user.config;

import com.hyang.ich.user.entity.SysUser;
import com.hyang.ich.user.mapper.system.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private SysUserMapper sysUserMapper;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public void run(String... args) {
        SysUser admin = sysUserMapper.selectByUsername("admin");
        if (admin == null) {
            SysUser user = new SysUser();
            user.setUsername("admin");
            user.setPassword(encoder.encode("admin123"));
            user.setNickname("Super Admin");
            user.setEmail("admin@ich.com");
            user.setPhone("13800138000");
            user.setStatus(1);
            sysUserMapper.insert(user);
            log.info("====== Admin account created: admin / admin123 ======");
        } else {
            sysUserMapper.update(createPasswordUpdate(admin.getId()));
            log.info("====== Admin account exists, password reset to: admin123 ======");
        }
    }

    private SysUser createPasswordUpdate(Long id) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setPassword(encoder.encode("admin123"));
        return u;
    }
}
