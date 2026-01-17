package com.printflow.config;

import com.printflow.entity.User;
import com.printflow.entity.User.Role;
import com.printflow.entity.enums.Language;
import com.printflow.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {
    
    // Ručni Logger umesto @Slf4j
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    
    private final PasswordEncoder passwordEncoder;

    // Ručni konstruktor umesto @RequiredArgsConstructor
    public DataInitializer(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }
    
    @Bean
    public CommandLineRunner initData(UserRepository userRepository) {
        return args -> {
            // Kreiranje admin korisnika ako ne postoji
            if (userRepository.findByUsername("admin").isEmpty()) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setFullName("System Administrator");
                admin.setEmail("admin@printflow.com");
                admin.setPhone("+381 11 123 4567");
                admin.setRole(Role.ADMIN);
                admin.setLanguage(Language.SR);
                admin.setActive(true);
                
                userRepository.save(admin);
                log.info("Created admin user: admin/admin123");
            }
            
            // Kreiranje dizajner korisnika
            if (userRepository.findByUsername("designer").isEmpty()) {
                User designer = new User();
                designer.setUsername("designer");
                designer.setPassword(passwordEncoder.encode("designer123"));
                designer.setFullName("Jane Designer");
                designer.setEmail("designer@printflow.com");
                designer.setPhone("+381 11 123 4568");
                designer.setRole(Role.WORKER_DESIGN);
                designer.setLanguage(Language.SR);
                designer.setActive(true);
                
                userRepository.save(designer);
                log.info("Created designer user: designer/designer123");
            }
            
            // Kreiranje štampar korisnika
            if (userRepository.findByUsername("printer").isEmpty()) {
                User printer = new User();
                printer.setUsername("printer");
                printer.setPassword(passwordEncoder.encode("printer123"));
                printer.setFullName("John Printer");
                printer.setEmail("printer@printflow.com");
                printer.setPhone("+381 11 123 4569");
                printer.setRole(Role.WORKER_PRINT);
                printer.setLanguage(Language.SR);
                printer.setActive(true);
                
                userRepository.save(printer);
                log.info("Created printer user: printer/printer123");
            }
            
            // Kreiranje general radnika
            if (userRepository.findByUsername("worker").isEmpty()) {
                User worker = new User();
                worker.setUsername("worker");
                worker.setPassword(passwordEncoder.encode("worker123"));
                worker.setFullName("General Worker");
                worker.setEmail("worker@printflow.com");
                worker.setPhone("+381 11 123 4570");
                worker.setRole(Role.WORKER_GENERAL);
                worker.setLanguage(Language.SR);
                worker.setActive(true);
                
                userRepository.save(worker);
                log.info("Created worker user: worker/worker123");
            }
            
            log.info("Data initialization completed");
        };
    }
}