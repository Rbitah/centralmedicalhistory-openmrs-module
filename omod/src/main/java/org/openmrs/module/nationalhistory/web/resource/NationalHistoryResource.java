package org.openmrs.module.nationalhistory.web.resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.nationalhistory.api.NationalHistoryService;
import org.openmrs.module.nationalhistory.web.representation.NationalHistoryResult;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.FullRepresentation;
import org.openmrs.module.webservices.rest.web.representation.RefRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

@Resource(name = RestConstants.VERSION_1 + "/nationalhistory", supportedClass = NationalHistoryResult.class,
        supportedOpenmrsVersions = { "1.8.* - 2.*" })
public class NationalHistoryResource extends DelegatingCrudResource<NationalHistoryResult> {

    private static final Log log = LogFactory.getLog(NationalHistoryResource.class);

    @Override
    public NationalHistoryResult getByUniqueId(String uniqueId) {
        requireAuthenticatedSession();

        try {
            NationalHistoryService service = Context.getService(NationalHistoryService.class);
            return new NationalHistoryResult(service.getHistoryRecordsForPatient(uniqueId));
        }
        catch (APIException ex) {
            log.warn("Unable to retrieve national history for patient " + uniqueId + ": " + ex.getMessage());
            throw new APIException("Unable to retrieve national history at this time");
        }
        catch (Exception ex) {
            log.error("Unexpected error while retrieving national history", ex);
            throw new APIException("Unable to retrieve national history at this time");
        }
    }

    @Override
    protected void delete(NationalHistoryResult delegate, String reason, RequestContext context) throws ResponseException {
        throw new ResourceDoesNotSupportOperationException();
    }

    @Override
    public void purge(NationalHistoryResult delegate, RequestContext context) throws ResponseException {
        throw new ResourceDoesNotSupportOperationException();
    }

    @Override
    public NationalHistoryResult newDelegate() {
        return new NationalHistoryResult();
    }

    @Override
    public NationalHistoryResult save(NationalHistoryResult delegate) {
        throw new ResourceDoesNotSupportOperationException();
    }

    @Override
    public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
        if (rep instanceof DefaultRepresentation || rep instanceof FullRepresentation || rep instanceof RefRepresentation) {
            DelegatingResourceDescription description = new DelegatingResourceDescription();
            description.addProperty("records");
            return description;
        }
        return null;
    }

    @Override
    protected PageableResult doGetAll(RequestContext context) throws ResponseException {
        throw new ResourceDoesNotSupportOperationException();
    }

    @Override
    public DelegatingResourceDescription getCreatableProperties() {
        throw new ResourceDoesNotSupportOperationException();
    }

    @Override
    public DelegatingResourceDescription getUpdatableProperties() {
        throw new ResourceDoesNotSupportOperationException();
    }

    private void requireAuthenticatedSession() {
        if (!Context.isAuthenticated()) {
            throw new APIAuthenticationException("Authentication is required");
        }
    }
}
