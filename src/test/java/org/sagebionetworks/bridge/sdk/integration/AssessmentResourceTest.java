package org.sagebionetworks.bridge.sdk.integration;

import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.rest.model.ResourceCategory.DATA_REPOSITORY;
import static org.sagebionetworks.bridge.rest.model.ResourceCategory.LICENSE;
import static org.sagebionetworks.bridge.rest.model.ResourceCategory.WEBSITE;
import static org.sagebionetworks.bridge.rest.model.Role.DEVELOPER;
import static org.sagebionetworks.bridge.rest.model.Role.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.sdk.integration.Tests.ORG_ID_1;
import static org.sagebionetworks.bridge.sdk.integration.Tests.ORG_ID_2;
import static org.sagebionetworks.bridge.sdk.integration.Tests.randomIdentifier;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.rest.api.AssessmentsApi;
import org.sagebionetworks.bridge.rest.api.OrganizationsApi;
import org.sagebionetworks.bridge.rest.api.SharedAssessmentsApi;
import org.sagebionetworks.bridge.rest.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.rest.model.Assessment;
import org.sagebionetworks.bridge.rest.model.AssessmentList;
import org.sagebionetworks.bridge.rest.model.ExternalResource;
import org.sagebionetworks.bridge.rest.model.PagedExternalResourceList;
import org.sagebionetworks.bridge.rest.model.RequestParams;
import org.sagebionetworks.bridge.user.TestUserHelper;
import org.sagebionetworks.bridge.user.TestUserHelper.TestUser;

public class AssessmentResourceTest {

    private static final String URL1 = "https://www.synapse.org/#!Synapse:syn4993293/wiki/247859";
    private static final String TITLE1 = "mPower Public Research Portal";
    private static final String URL2 = "https://parkinsonmpower.org/your-story";
    private static final String TITLE2 = "mPower Website";
    private TestUser admin;
    private TestUser developerOrg1;
    private TestUser developerOrg2;
    private TestUser studyDesignerOrg1;
    private TestUser studyDesignerOrg2;
    private String id;
    private OrganizationsApi orgsApi;
    private AssessmentsApi assessmentApiOrg1;
    private AssessmentsApi assessmentApiOrg2;
    private SharedAssessmentsApi sharedAssessmentsApiOrg1;

    @Before
    public void before() throws Exception {
        id = randomIdentifier(AssessmentResourceTest.class);
        admin = TestUserHelper.getSignedInAdmin();
        orgsApi = admin.getClient(OrganizationsApi.class);
    }

    @After
    public void after() throws IOException {
        if (developerOrg1 != null) {
            developerOrg1.signOutAndDeleteUser();
        }
        if (developerOrg2 != null) {
            developerOrg2.signOutAndDeleteUser();
        }
        if (studyDesignerOrg1 != null) {
            studyDesignerOrg1.signOutAndDeleteUser();
        }
        if (studyDesignerOrg2 != null) {
            studyDesignerOrg2.signOutAndDeleteUser();
        }

        TestUser admin = TestUserHelper.getSignedInAdmin();
        AssessmentsApi api = admin.getClient(AssessmentsApi.class);
        SharedAssessmentsApi sharedApi = admin.getClient(SharedAssessmentsApi.class);

        AssessmentList revisions = api.getAssessmentRevisionsById(id, 0, 10, true).execute().body();
        for (Assessment oneRevision : revisions.getItems()) {
            api.deleteAssessment(oneRevision.getGuid(), true).execute();
        }
        
        try {
            revisions = sharedApi.getSharedAssessmentRevisionsById(id, null, null, true).execute().body();
            for (Assessment oneRevision : revisions.getItems()) {
                sharedApi.deleteSharedAssessment(oneRevision.getGuid(), true).execute();
            }
        } catch(EntityNotFoundException e) {
        }
    }

    @Test
    public void testForDeveloper() throws Exception {
        developerOrg1 = new TestUserHelper.Builder(AssessmentResourceTest.class).withRoles(DEVELOPER).createAndSignInUser();
        orgsApi.addMember(ORG_ID_1, developerOrg1.getUserId()).execute();
        
        developerOrg2 = new TestUserHelper.Builder(AssessmentResourceTest.class).withRoles(DEVELOPER)
                .createAndSignInUser();
        orgsApi.addMember(ORG_ID_2, developerOrg2.getUserId()).execute();
        
        assessmentApiOrg1 = developerOrg1.getClient(AssessmentsApi.class);
        sharedAssessmentsApiOrg1 = developerOrg1.getClient(SharedAssessmentsApi.class);
        assessmentApiOrg2 = developerOrg2.getClient(AssessmentsApi.class);
        
        Assessment unsavedAssessment = new Assessment().identifier(id).title("AssessmentResourceTest assessment")
                .osName("Android").ownerId(ORG_ID_1).revision(1L);

        Assessment assessment = assessmentApiOrg1.createAssessment(unsavedAssessment).execute().body();
        
        ExternalResource resource1 = new ExternalResource().title(TITLE1).url(URL1).category(DATA_REPOSITORY)
                .minRevision(1).maxRevision(1).publishers(ImmutableList.of("Sage Bionetworks"));
        ExternalResource resource2 = new ExternalResource().title(TITLE2).url(URL2).category(WEBSITE)
                .minRevision(1).maxRevision(10).publishers(ImmutableList.of("Sage Bionetworks"));

        // create a resource
        resource1 = assessmentApiOrg1.createAssessmentResource(id, resource1).execute().body();
        assertNotNull(resource1.getGuid());
        assertEquals(TITLE1, resource1.getTitle());
        assertEquals(URL1, resource1.getUrl());
        assertTrue(resource1.isUpToDate());
        Long version = resource1.getVersion();
        
        String resourceGuid = resource1.getGuid();
        
        // create this for later         
        resource2 = assessmentApiOrg1.createAssessmentResource(id, resource2).execute().body();

        // retrieve the resources and test some fields
        PagedExternalResourceList resourcesPage = assessmentApiOrg1.getAssessmentResources(
                id, null, null, null, null, null, false).execute().body();
        assertEquals(2, resourcesPage.getItems().size());
        assertEquals(Integer.valueOf(2), resourcesPage.getTotal());
        resource1 = resourcesPage.getItems().get(0);
        assertEquals(TITLE1, resource1.getTitle());
        assertEquals(URL1, resource1.getUrl());
        assertTrue(resource1.isUpToDate());
        assertEquals(version, resource1.getVersion());
        
        // retrieving by min/max works
        resourcesPage = assessmentApiOrg1.getAssessmentResources(
                id, null, null, null, null, 1, false).execute().body();
        assertEquals(2, resourcesPage.getItems().size());
        
        resourcesPage = assessmentApiOrg1.getAssessmentResources(
                id, null, null, null, 2, null, false).execute().body();
        assertEquals(1, resourcesPage.getItems().size());
        assertEquals(WEBSITE, resourcesPage.getItems().get(0).getCategory());

        // change and update assessment the assessment
        assessment.setTitle("new title");
        assessment.setRevision(2L);
        assessment = assessmentApiOrg1.updateAssessment(assessment.getGuid(), assessment).execute().body();
        
        // verify the resource is no longer up-to-date
        resource1 = assessmentApiOrg1.getAssessmentResource(id, resourceGuid).execute().body();
        assertFalse(resource1.isUpToDate());
        
        // update the resource. this should increment the version.
        resource1.setUrl(URL1 + "2");
        resource1 = assessmentApiOrg1.updateAssessmentResource(id, resourceGuid, resource1).execute().body();
        assertEquals(URL1 + "2", resource1.getUrl());
        assertNotEquals(version, resource1.getVersion());
        assertTrue(resource1.isUpToDate());
        
        // Does retrieve item with right category and min/max revision set (all matching)
        resourcesPage = assessmentApiOrg1.getAssessmentResources(id, null, null,
                ImmutableList.of("data_repository"), 1, 1, null).execute().body();
        assertFalse(resourcesPage.getItems().isEmpty());

        // minRevision is too high to match
        resourcesPage = assessmentApiOrg1.getAssessmentResources(id, null, null, 
                null, 20, null, null).execute().body();
        assertTrue(resourcesPage.getItems().isEmpty());

        // maxRevision is higher and that's fine
        resourcesPage = assessmentApiOrg1.getAssessmentResources(id, null, null, 
                null, null, 20, null).execute().body();
        assertFalse(resourcesPage.getItems().isEmpty());

        // category doesn't match
        resourcesPage = assessmentApiOrg1.getAssessmentResources(id, null, null,
                ImmutableList.of("license"), null, null, null).execute().body();
        assertTrue(resourcesPage.getItems().isEmpty());
        
        RequestParams rp = resourcesPage.getRequestParams();
        assertEquals(ImmutableList.of(LICENSE), rp.getCategories());
        assertEquals(Integer.valueOf(50), rp.getPageSize());
        assertEquals(Integer.valueOf(0), rp.getOffsetBy());
        assertFalse(rp.isIncludeDeleted());

        // Other developers can see local resources, just as they can see the assessment
        resourcesPage = assessmentApiOrg2.getAssessmentResources(
                id, null, null, null, null, null, null).execute().body();
        assertFalse(resourcesPage.getItems().isEmpty());
        
        // They can update these resources as well
        resource1.setTitle("This will be persisted");
        ExternalResource updatedRes1 = assessmentApiOrg2.updateAssessmentResource(id, resourceGuid, resource1).execute().body();
        assertEquals("This will be persisted", updatedRes1.getTitle());

        // publish the assessment and resource
        assessmentApiOrg1.publishAssessment(assessment.getGuid(), null).execute().body();
        assessmentApiOrg1.publishAssessmentResource(id, ImmutableList.of(resourceGuid)).execute().body();

        // Resource should be published along with the assessment.
        PagedExternalResourceList sharedResourcesPage = sharedAssessmentsApiOrg1.getSharedAssessmentResources(
                id, null, null, null, null, null, null).execute().body();
        assertEquals(1, sharedResourcesPage.getItems().size());
        assertEquals("This will be persisted", sharedResourcesPage.getItems().get(0).getTitle());
        
        // publish assessment resource again. the existing record should be updated
        String newTitle = "Different Title: " + randomIdentifier(AssessmentResourceTest.class);
        resource1.setTitle(newTitle);
        resource1.setVersion(updatedRes1.getVersion());
        resource1 = assessmentApiOrg1.updateAssessmentResource(id, resourceGuid, resource1).execute().body();

        ExternalResource sharedResource = assessmentApiOrg1.publishAssessmentResource(id, 
                ImmutableList.of(resourceGuid)).execute().body().getItems().get(0);
        
        sharedResourcesPage = sharedAssessmentsApiOrg1.getSharedAssessmentResources(id, null, null, 
                null, null, null, true).execute().body();
        assertEquals(newTitle, sharedResourcesPage.getItems().get(0).getTitle());
        
        // get individual shared assessment
        sharedResource = sharedAssessmentsApiOrg1.getSharedAssessmentResource(id, sharedResource.getGuid()).execute().body();
        assertNotNull(sharedResource);
        
        // import the assessment and resource back to the local context.
        sharedResource.setTitle(newTitle);
        sharedResource = sharedAssessmentsApiOrg1.updateSharedAssessmentResource(
                id, resourceGuid, sharedResource).execute().body();
        sharedAssessmentsApiOrg1.importSharedAssessmentResource(id, ImmutableList.of(resourceGuid)).execute();

        resourcesPage = assessmentApiOrg1.getAssessmentResources(id, null, null, null, null, null, true).execute().body();
        assertTrue(resourcesPage.getItems().stream().anyMatch(res -> res.getTitle().equals(newTitle)));
        
        // Okay, after these copies, there should still be two resources for the local and for the shared objects
        int localCount = assessmentApiOrg1.getAssessmentResources(id, null, null, null, null, null, false).execute().body()
                .getTotal();
        int sharedCount = sharedAssessmentsApiOrg1.getSharedAssessmentResources(id, null, null, null, null, null, false)
                .execute().body().getTotal();
        assertEquals(2, localCount);
        assertEquals(1, sharedCount);
        
        // logically delete the local resources
        assessmentApiOrg1.deleteAssessmentResource(id, resource1.getGuid(), false).execute();
        assessmentApiOrg1.deleteAssessmentResource(id, resource2.getGuid(), false).execute();
        localCount = assessmentApiOrg1.getAssessmentResources(
                id, null, null, null, null, null, false).execute().body().getTotal();
        assertEquals(0, localCount);
        localCount = assessmentApiOrg1.getAssessmentResources(
                id, null, null, null, null, null, true).execute().body().getTotal();
        assertEquals(2, localCount);
        // can still be retrieved with knowledge of the GUID
        resource1 = assessmentApiOrg1.getAssessmentResource(id, resource1.getGuid()).execute().body();
        assertNotNull(resource1);
        
        // logically delete the shared resource
        admin.getClient(SharedAssessmentsApi.class).deleteSharedAssessmentResource(
                id, sharedResource.getGuid(), false).execute();
        sharedCount = sharedAssessmentsApiOrg1.getSharedAssessmentResources(
                id, null, null, null, null, null, false).execute().body().getTotal();
        assertEquals(0, sharedCount);
        sharedCount = sharedAssessmentsApiOrg1.getSharedAssessmentResources(
                id, null, null, null, null, null, true).execute().body().getTotal();
        assertEquals(1, sharedCount);
        sharedResource = sharedAssessmentsApiOrg1.getSharedAssessmentResource(id, sharedResource.getGuid()).execute().body();
        assertNotNull(sharedResource);
        
        // test paging values
        for (int i=0; i < 10; i++) {
            ExternalResource res = assessmentApiOrg1.createAssessmentResource(id, resource1).execute().body();;
            assessmentApiOrg1.publishAssessmentResource(id, ImmutableList.of(res.getGuid())).execute();
        }
        PagedExternalResourceList page1 = assessmentApiOrg1.getAssessmentResources(id, 0, 5, null, null, null, false)
                .execute().body();        
        PagedExternalResourceList page2 = assessmentApiOrg1.getAssessmentResources(id, 5, 5, null, null, null, false)
                .execute().body();
        PagedExternalResourceList page3 = assessmentApiOrg1.getAssessmentResources(id, 10, 5, null, null, null, false)
                .execute().body();
        // should be 10 unique GUIDs, last page notwithstanding
        Set<String> allGuids = new HashSet<>();
        allGuids.addAll(page1.getItems().stream().map(ExternalResource::getGuid).collect(toSet()));
        allGuids.addAll(page2.getItems().stream().map(ExternalResource::getGuid).collect(toSet()));
        allGuids.addAll(page3.getItems().stream().map(ExternalResource::getGuid).collect(toSet()));
        assertEquals(10, allGuids.size());
        
        page1 = sharedAssessmentsApiOrg1.getSharedAssessmentResources(id, 0, 5, null, null, null, false)
                .execute().body();        
        page2 = sharedAssessmentsApiOrg1.getSharedAssessmentResources(id, 5, 5, null, null, null, false)
                .execute().body();
        page3 = sharedAssessmentsApiOrg1.getSharedAssessmentResources(id, 10, 5, null, null, null, false)
                .execute().body();
        // should be 10 unique GUIDs, last page notwithstanding
        allGuids.clear();
        allGuids.addAll(page1.getItems().stream().map(ExternalResource::getGuid).collect(toSet()));
        allGuids.addAll(page2.getItems().stream().map(ExternalResource::getGuid).collect(toSet()));
        allGuids.addAll(page3.getItems().stream().map(ExternalResource::getGuid).collect(toSet()));
        assertEquals(10, allGuids.size());
    }
    
    @Test
    public void testForStudyDesigner() throws Exception {
        studyDesignerOrg1 = new TestUserHelper.Builder(AssessmentResourceTest.class).withRoles(STUDY_DESIGNER).createAndSignInUser();
        orgsApi.addMember(ORG_ID_1, studyDesignerOrg1.getUserId()).execute();
        
        studyDesignerOrg2 = new TestUserHelper.Builder(AssessmentResourceTest.class).withRoles(STUDY_DESIGNER)
                .createAndSignInUser();
        orgsApi.addMember(ORG_ID_2, studyDesignerOrg2.getUserId()).execute();
        
        assessmentApiOrg1 = studyDesignerOrg1.getClient(AssessmentsApi.class);
        assessmentApiOrg2 = studyDesignerOrg2.getClient(AssessmentsApi.class);
        
        Assessment unsavedAssessment = new Assessment().identifier(id).title("AssessmentResourceTest assessment")
                .osName("Android").ownerId(ORG_ID_1).revision(1L);

        assessmentApiOrg1.createAssessment(unsavedAssessment).execute().body();
        
        ExternalResource resource1 = new ExternalResource().title(TITLE1).url(URL1).category(DATA_REPOSITORY)
                .minRevision(1).maxRevision(1).publishers(ImmutableList.of("Sage Bionetworks"));
        ExternalResource resource2 = new ExternalResource().title(TITLE2).url(URL2).category(WEBSITE)
                .minRevision(1).maxRevision(10).publishers(ImmutableList.of("Sage Bionetworks"));

        // create a resource
        resource1 = assessmentApiOrg1.createAssessmentResource(id, resource1).execute().body();
        assertNotNull(resource1.getGuid());
        assertEquals(TITLE1, resource1.getTitle());
        assertEquals(URL1, resource1.getUrl());
        assertTrue(resource1.isUpToDate());
        
        String resourceGuid = resource1.getGuid();
        
        // create this for test          
        resource2 = assessmentApiOrg1.createAssessmentResource(id, resource2).execute().body();
        
        // Other study designer cannot see this local resource because the assessment is not accessible
        try {
            assessmentApiOrg2.getAssessmentResources(
                    id, null, null, null, null, null, null).execute().body();
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }

        // They also cannot update the resource for the same reason (assessment not visible)
        try {
            resource1.setTitle("This will be persisted");
            assessmentApiOrg2.updateAssessmentResource(id, resourceGuid, resource1).execute().body();
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            
        }
    }
}
