package edu.zzttc.backend.domain.dto;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
@AllArgsConstructor
public class ConfirmResetDTO {
    @Email
    String email;
    @Length(min = 6, max = 6)
    String code;
}
