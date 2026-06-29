package lt.satsyuk.repository;

import lt.satsyuk.model.Client;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    boolean existsByPhone(String phone);

    @Query("SELECT c FROM Client c JOIN ClientAccess ca ON ca.clientId = c.id WHERE c.id = :id AND ca.authClientId = :authClientId")
    Optional<Client> findByIdAndAuthClientId(@Param("id") Long id, @Param("authClientId") String authClientId);

    @Query("SELECT c FROM Client c JOIN ClientAccess ca ON ca.clientId = c.id WHERE ca.authClientId = :authClientId AND (LOWER(c.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(c.lastName) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Client> searchByNameOrSurnameAndAuthClientId(
            @Param("query") String query,
            @Param("authClientId") String authClientId,
            Pageable pageable);
}
