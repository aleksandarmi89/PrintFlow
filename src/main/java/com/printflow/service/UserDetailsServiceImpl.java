package com.printflow.service;

import com.printflow.entity.User;
import com.printflow.security.CustomUserPrincipal;
import com.printflow.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    
    private final UserRepository userRepository;
    
    
    public UserDetailsServiceImpl(UserRepository userRepository) {
		
		this.userRepository = userRepository;
	}


	public UserRepository getUserRepository() {
		return userRepository;
	}


	@Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        
        if (!user.isActive()) {
            throw new UsernameNotFoundException("User is inactive: " + username);
        }
        if (user.getCompany() != null && !user.getCompany().isActive()) {
            throw new UsernameNotFoundException("Company is inactive: " + username);
        }
        
        return new CustomUserPrincipal(
            user.getId(),
            user.getCompany() != null ? user.getCompany().getId() : null,
            user.getUsername(),
            user.getPassword(),
            Collections.singletonList(new SimpleGrantedAuthority(user.getRole().name())),
            user.isActive()
        );
    }
}
