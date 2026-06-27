package lt.satsyuk.repository;

import lt.satsyuk.model.Request;
import lt.satsyuk.model.RequestId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RequestRepository extends JpaRepository<Request, RequestId> {

    @Query("SELECT r FROM Request r WHERE r.requestId.id = :id")
    Optional<Request> findById(@Param("id") UUID id);
}
