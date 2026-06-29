package lt.satsyuk.repository;

import lt.satsyuk.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @Query("select a from Account a join fetch a.client where a.client.id = :clientId")
    Optional<Account> findByClientId(@Param("clientId") Long clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a join fetch a.client where a.client.id = :clientId")
    Optional<Account> findByClientIdForPessimisticUpdate(@Param("clientId") Long clientId);

    @Query("select a from Account a join fetch a.client c JOIN ClientAccess ca ON ca.clientId = c.id where a.client.id = :clientId and ca.authClientId = :authClientId")
    Optional<Account> findByClientIdAndAuthClientId(@Param("clientId") Long clientId, @Param("authClientId") String authClientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a join fetch a.client c JOIN ClientAccess ca ON ca.clientId = c.id where a.client.id = :clientId and ca.authClientId = :authClientId")
    Optional<Account> findByClientIdAndAuthClientIdForPessimisticUpdate(@Param("clientId") Long clientId, @Param("authClientId") String authClientId);
}
