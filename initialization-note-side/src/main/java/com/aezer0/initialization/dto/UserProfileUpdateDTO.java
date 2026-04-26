package com.aezer0.initialization.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserProfileUpdateDTO {

    @Size(max = 50, message = "username length cannot exceed 50")
    private String username;

    @Size(max = 255, message = "avatar url length cannot exceed 255")
    private String avatarUrl;

    @Size(max = 50, message = "real name length cannot exceed 50")
    private String realName;

    @Min(value = 0, message = "gender must be 0, 1 or 2")
    @Max(value = 2, message = "gender must be 0, 1 or 2")
    private Integer gender;

    /**
     * yyyy-MM-dd
     */
    private String birthDate;

    @Min(value = 0, message = "work years cannot be negative")
    @Max(value = 60, message = "work years cannot exceed 60")
    private Integer workYears;

    @Min(value = 1, message = "education must be between 1 and 5")
    @Max(value = 5, message = "education must be between 1 and 5")
    private Integer education;
}
