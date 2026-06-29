CREATE TABLE client_access (
    client_id       BIGINT NOT NULL REFERENCES client(id),
    auth_client_id  VARCHAR(255) NOT NULL,
    PRIMARY KEY (client_id, auth_client_id)
);

CREATE INDEX idx_client_access_auth_client_id ON client_access(auth_client_id);
