package lt.satsyuk.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "client_access")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(ClientAccessId.class)
public class ClientAccess {

    @Id
    @Column(name = "client_id")
    private Long clientId;

    @Id
    @Column(name = "auth_client_id")
    private String authClientId;
}
