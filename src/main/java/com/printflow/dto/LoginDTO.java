package com.printflow.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
public class LoginDTO {
    private String username;
    private String password;
    private boolean rememberMe;
    
    
    
	public LoginDTO() {
		
		// TODO Auto-generated constructor stub
	}
	public LoginDTO(String username, String password, boolean rememberMe) {
	
		this.username = username;
		this.password = password;
		this.rememberMe = rememberMe;
	}
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public boolean isRememberMe() {
		return rememberMe;
	}
	public void setRememberMe(boolean rememberMe) {
		this.rememberMe = rememberMe;
	}
    
    
}