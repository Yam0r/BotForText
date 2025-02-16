package user.inn_bot.repository;

import user.inn_bot.model.Inn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface InnRepository extends JpaRepository<Inn, Long>{
    Optional<Inn> findByInn(String inn);
    boolean existsByInn(String inn);
}
