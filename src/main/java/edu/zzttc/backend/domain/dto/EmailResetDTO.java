package edu.zzttc.backend.domain.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class EmailResetDTO {
    @Email
    private String email;
    @Length(min = 6, max = 6)
    private String code;
    @Length(min = 6, max = 20)
    private String password;
}
