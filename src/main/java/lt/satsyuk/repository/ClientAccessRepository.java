package lt.satsyuk.repository;

import lt.satsyuk.model.ClientAccess;
import lt.satsyuk.model.ClientAccessId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientAccessRepository extends JpaRepository<ClientAccess, ClientAccessId> {

    boolean existsByClientIdAndAuthClientId(Long clientId, String authClientId);
}
