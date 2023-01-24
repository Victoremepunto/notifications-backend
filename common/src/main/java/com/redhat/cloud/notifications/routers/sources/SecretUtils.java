package com.redhat.cloud.notifications.routers.sources;

import com.redhat.cloud.notifications.XRhIdentityUtils;
import com.redhat.cloud.notifications.models.BasicAuthentication;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.SourcesSecretable;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.UUID;

@ApplicationScoped
public class SecretUtils {

    /**
     * Used to manage the secrets on Sources.
     */
    @Inject
    @RestClient
    SourcesService sourcesService;

    /**
     * Loads the endpoint's secrets from Sources.
     * @param endpoint the endpoint to get the secrets from.
     */
    public void loadSecretsForEndpoint(Endpoint endpoint) {
        EndpointProperties endpointProperties = endpoint.getProperties();

        if (endpointProperties instanceof SourcesSecretable) {
            var props = (SourcesSecretable) endpointProperties;

            final Long basicAuthSourcesId = props.getBasicAuthenticationSourcesId();
            if (basicAuthSourcesId != null) {
                final String xRhIdentityContents = XRhIdentityUtils.generateEncodedXRhIdentity(endpoint.getOrgId());

                // Log the org ID and the contents of the x-rh-identity header we are about to send.
                Log.debugf("[endpoint_id: %s][org_id: %s] the encoded x-rh-identity contents are '%s'", endpoint.getId(), endpoint.getOrgId(), xRhIdentityContents);

                final Secret secret = this.sourcesService.getById(
                    xRhIdentityContents,
                    basicAuthSourcesId
                );

                props.setBasicAuthentication(
                    new BasicAuthentication(
                        secret.username,
                        secret.password
                    )
                );
            }

            final Long secretTokenSourcesId = props.getSecretTokenSourcesId();
            if (secretTokenSourcesId != null) {
                final String xRhIdentityContents = XRhIdentityUtils.generateEncodedXRhIdentity(endpoint.getOrgId());

                // Log the org ID and the contents of the x-rh-identity header we are about to send.
                Log.debugf("[endpoint_id: %s][org_id: %s] the encoded x-rh-identity contents are '%s'", endpoint.getId(), endpoint.getOrgId(), xRhIdentityContents);

                final Secret secret = this.sourcesService.getById(
                    xRhIdentityContents,
                    secretTokenSourcesId
                );

                props.setSecretToken(secret.password);
            }
        }
    }

    /**
     * Creates the endpoint's secrets in Sources.
     * @param endpoint the endpoint to create the secrets from.
     */
    public void createSecretsForEndpoint(Endpoint endpoint) {
        EndpointProperties endpointProperties = endpoint.getProperties();

        if (endpointProperties instanceof SourcesSecretable) {
            var props = (SourcesSecretable) endpointProperties;

            final BasicAuthentication basicAuth = props.getBasicAuthentication();
            if (!this.isBasicAuthNullOrBlank(basicAuth)) {
                final long id = this.createBasicAuthentication(endpoint.getId(), endpoint.getOrgId(), basicAuth);

                Log.infof("[secret_id: %s] Basic authentication secret created in Sources", id);

                props.setBasicAuthenticationSourcesId(id);
            }

            final String secretToken = props.getSecretToken();
            if (secretToken != null && !secretToken.isBlank()) {
                final long id = this.createSecretTokenSecret(endpoint.getId(), endpoint.getOrgId(), secretToken);

                Log.infof("[secret_id: %s] Secret token secret created in Sources", id);

                props.setSecretTokenSourcesId(id);
            }
        }
    }

    /**
     * <p>Updates the endpoint's secrets in Sources. However a few cases are covered for the secrets:</p>
     * <ul>
     *  <li>If the endpoint has an ID for the secret, and the incoming secret is {@code null}, it is assumed that the
     *  user wants the secret to be deleted.</li>
     *  <li>If the endpoint has an ID for the secret, and the incoming secret isn't {@code null}, then the secret is
     *  updated.</li>
     *  <li>If the endpoint doesn't have an ID for the secret, and the incoming secret is {@code null}, it's basically
     *  a NOP — although the attempt is logged for debugging purposes.</li>
     *  <li>If the endpoint doesn't have an ID for the secret, and the incoming secret isn't {@code null}, it is
     *  assumed that the user wants the secret to be created.</li>
     * </ul>
     * @param endpoint the endpoint to update the secrets from.
     */
    public void updateSecretsForEndpoint(Endpoint endpoint) {
        EndpointProperties endpointProperties = endpoint.getProperties();

        if (endpointProperties instanceof SourcesSecretable) {
            var props = (SourcesSecretable) endpointProperties;

            final BasicAuthentication basicAuth = props.getBasicAuthentication();
            final Long basicAuthId = props.getBasicAuthenticationSourcesId();
            if (basicAuthId != null) {
                if (this.isBasicAuthNullOrBlank(basicAuth)) {
                    final String xRhIdentityContents = XRhIdentityUtils.generateEncodedXRhIdentity(endpoint.getOrgId());

                    // Log the org ID and the contents of the x-rh-identity header we are about to send.
                    Log.debugf("[endpoint_id: %s][org_id: %s] the encoded x-rh-identity contents are '%s'", endpoint.getId(), endpoint.getOrgId(), xRhIdentityContents);

                    this.sourcesService.delete(
                        xRhIdentityContents,
                        basicAuthId
                    );
                    Log.infof("[endpoint_id: %s][secret_id: %s] Basic authentication secret deleted in Sources during an endpoint update operation", endpoint.getId(), basicAuthId);

                    props.setBasicAuthenticationSourcesId(null);
                } else {
                    Secret secret = new Secret();

                    secret.password = basicAuth.getPassword();
                    secret.username = basicAuth.getUsername();

                    final String xRhIdentityContents = XRhIdentityUtils.generateEncodedXRhIdentity(endpoint.getOrgId());

                    // Log the org ID and the contents of the x-rh-identity header we are about to send.
                    Log.debugf("[endpoint_id: %s][org_id: %s] the encoded x-rh-identity contents are '%s'", endpoint.getId(), endpoint.getOrgId(), xRhIdentityContents);

                    this.sourcesService.update(
                        xRhIdentityContents,
                        basicAuthId,
                        secret
                    );
                    Log.infof("[endpoint_id: %s][secret_id: %s] Basic authentication secret updated in Sources during an endpoint update operation", endpoint.getId(), basicAuthId);
                }
            } else {
                if (this.isBasicAuthNullOrBlank(basicAuth)) {
                    Log.debugf("[endpoint_id: %s] Basic authentication secret not created in Sources: the basic authentication object is null", endpoint.getId());
                } else {
                    final long id = this.createBasicAuthentication(endpoint.getId(), endpoint.getOrgId(), basicAuth);
                    Log.infof("[endpoint_id: %s][secret_id: %s] Basic authentication secret created in Sources during an endpoint update operation", endpoint.getId(), id);

                    props.setBasicAuthenticationSourcesId(id);
                }
            }

            final String secretToken = props.getSecretToken();
            final Long secretTokenId = props.getSecretTokenSourcesId();
            if (secretTokenId != null) {
                if (secretToken == null || secretToken.isBlank()) {
                    final String xRhIdentityContents = XRhIdentityUtils.generateEncodedXRhIdentity(endpoint.getOrgId());

                    // Log the org ID and the contents of the x-rh-identity header we are about to send.
                    Log.debugf("[endpoint_id: %s][org_id: %s] the encoded x-rh-identity contents are '%s'", endpoint.getId(), endpoint.getOrgId(), xRhIdentityContents);

                    this.sourcesService.delete(
                        xRhIdentityContents,
                        secretTokenId
                    );

                    props.setSecretTokenSourcesId(null);

                    Log.infof("[endpoint_id: %s][secret_id: %s] Secret token secret deleted in Sources during an endpoint update operation", endpoint.getId(), secretTokenId);
                } else {
                    Secret secret = new Secret();

                    secret.password = secretToken;

                    final String xRhIdentityContents = XRhIdentityUtils.generateEncodedXRhIdentity(endpoint.getOrgId());

                    // Log the org ID and the contents of the x-rh-identity header we are about to send.
                    Log.debugf("[endpoint_id: %s][org_id: %s] the encoded x-rh-identity contents are '%s'", endpoint.getId(), endpoint.getOrgId(), xRhIdentityContents);

                    this.sourcesService.update(
                        xRhIdentityContents,
                        secretTokenId,
                        secret
                    );
                    Log.infof("[endpoint_id: %s][secret_id: %s] Secret token secret updated in Sources", endpoint.getId(), secretTokenId);
                }
            } else {
                if (secretToken == null || secretToken.isBlank()) {
                    Log.debugf("[endpoint_id: %s] Secret token secret not created in Sources: the secret token object is null or blank", endpoint.getId());
                } else {
                    final long id = this.createSecretTokenSecret(endpoint.getId(), endpoint.getOrgId(), secretToken);

                    Log.infof("[endpoint_id: %s][secret_id: %s] Secret token secret created in Sources during an endpoint update operation", endpoint.getId(), id);

                    props.setSecretTokenSourcesId(id);
                }
            }
        }
    }

    /**
     * Deletes the endpoint's secrets. It requires for the properties to have a "basic authentication" ID or "secret
     * token" ID on the database.
     * @param endpoint the endpoint to delete the secrets from.
     */
    public void deleteSecretsForEndpoint(Endpoint endpoint) {
        EndpointProperties endpointProperties = endpoint.getProperties();

        if (endpointProperties instanceof SourcesSecretable) {
            var props = (SourcesSecretable) endpointProperties;

            final Long basicAuthId = props.getBasicAuthenticationSourcesId();
            if (basicAuthId != null) {
                final String xRhIdentityContents = XRhIdentityUtils.generateEncodedXRhIdentity(endpoint.getOrgId());

                // Log the org ID and the contents of the x-rh-identity header we are about to send.
                Log.debugf("[endpoint_id: %s][org_id: %s] the encoded x-rh-identity contents are '%s'", endpoint.getId(), endpoint.getOrgId(), xRhIdentityContents);

                this.sourcesService.delete(
                    xRhIdentityContents,
                    basicAuthId
                );
                Log.infof("[endpoint_id: %s][secret_id: %s] Basic authentication secret updated in Sources", endpoint.getId(), basicAuthId);
            }

            final Long secretTokenId = props.getSecretTokenSourcesId();
            if (secretTokenId != null) {
                final String xRhIdentityContents = XRhIdentityUtils.generateEncodedXRhIdentity(endpoint.getOrgId());

                // Log the org ID and the contents of the x-rh-identity header we are about to send.
                Log.debugf("[endpoint_id: %s][org_id: %s] the encoded x-rh-identity contents are '%s'", endpoint.getId(), endpoint.getOrgId(), xRhIdentityContents);

                this.sourcesService.delete(
                    xRhIdentityContents,
                    secretTokenId
                );
                Log.infof("[endpoint_id: %s][secret_id: %s] Secret token secret deleted in Sources", endpoint.getId(), secretTokenId);
            }
        }
    }

    /**
     * Creates a "basic authentication" secret in Sources.
     * @param endpointId the endpoint's ID that will be simply logged.
     * @param orgId the organization ID for the x-rh-identity header's contents.
     * @param basicAuthentication the contents of the "basic authentication" secret.
     * @return the id of the created secret.
     */
    private long createBasicAuthentication(final UUID endpointId, final String orgId, final BasicAuthentication basicAuthentication) {
        Secret secret = new Secret();

        secret.authenticationType = Secret.TYPE_BASIC_AUTH;
        secret.password = basicAuthentication.getPassword();
        secret.username = basicAuthentication.getUsername();

        final String xRhIdentityContents = XRhIdentityUtils.generateEncodedXRhIdentity(orgId);

        // Log the org ID and the contents of the x-rh-identity header we are about to send.
        Log.debugf("[endpoint_id: %s][org_id: %s] the encoded x-rh-identity contents are '%s'", endpointId, orgId, xRhIdentityContents);

        final Secret createdSecret = this.sourcesService.create(
            xRhIdentityContents,
            secret
        );

        return createdSecret.id;
    }

    /**
     * Creates a "secret token" secret in Sources.
     * @param endpointId the endpoint's ID that will be simply logged.
     * @param orgId the organization ID for the x-rh-identity header's contents.
     * @param secretToken the "secret token"'s contents.
     * @return the id of the created secret.
     */
    private long createSecretTokenSecret(final UUID endpointId, final String orgId, final String secretToken) {
        Secret secret = new Secret();

        secret.authenticationType = Secret.TYPE_SECRET_TOKEN;
        secret.password = secretToken;

        final String xRhIdentityContents = XRhIdentityUtils.generateEncodedXRhIdentity(orgId);

        // Log the org ID and the contents of the x-rh-identity header we are about to send.
        Log.debugf("[endpoint_id: %s][org_id: %s] the encoded x-rh-identity contents are '%s'", endpointId, orgId, xRhIdentityContents);

        final Secret createdSecret = this.sourcesService.create(
            xRhIdentityContents,
            secret
        );

        return createdSecret.id;
    }

    /**
     * Checks whether the provided {@link BasicAuthentication} object is null, or if its inner password and username
     * fields are blank. If any of the username or password fields contain a non-blank string, then it is assumed that
     * the object is not blank.
     * @param basicAuthentication the object to check.
     * @return {@code true} if the object is null, or if the password and the username are blank.
     */
    protected boolean isBasicAuthNullOrBlank(final BasicAuthentication basicAuthentication) {
        if (basicAuthentication == null) {
            return true;
        }

        return (basicAuthentication.getPassword() == null || basicAuthentication.getPassword().isBlank()) &&
                (basicAuthentication.getUsername() == null || basicAuthentication.getUsername().isBlank());
    }
}
