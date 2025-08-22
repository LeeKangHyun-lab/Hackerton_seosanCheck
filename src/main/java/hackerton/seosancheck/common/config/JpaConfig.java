package hackerton.seosancheck.common.config; // 💡 패키지 경로를 실제 위치에 맞게 수정했습니다.

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.persistence.EntityManagerFactory;

@Configuration
// 💡 JPA 리포지토리가 있는 패키지 경로를 정확히 지정합니다.
@EnableJpaRepositories(basePackages = "hackerton.seosancheck.repository")
@EnableTransactionManagement
public class JpaConfig {

    /**
     * JPA를 위한 별도의 트랜잭션 관리자를 등록합니다.
     * 이렇게 하면 MyBatis의 트랜잭션 관리자와 충돌하지 않습니다.
     * @param entityManagerFactory Spring이 자동으로 주입해주는 EntityManagerFactory
     * @return JPA 전용 트랜잭션 관리자
     */
    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
