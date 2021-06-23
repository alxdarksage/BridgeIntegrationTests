package org.sagebionetworks.bridge.sdk.integration;

import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.rest.model.Role.DEVELOPER;
import static org.sagebionetworks.bridge.rest.model.Role.STUDY_DESIGNER;
import static org.sagebionetworks.bridge.sdk.integration.Tests.API_SIGNIN;
import static org.sagebionetworks.bridge.sdk.integration.Tests.ORG_ID_1;
import static org.sagebionetworks.bridge.sdk.integration.Tests.ORG_ID_2;
import static org.sagebionetworks.bridge.sdk.integration.Tests.SHARED_SIGNIN;
import static org.sagebionetworks.bridge.sdk.integration.Tests.randomIdentifier;
import static org.sagebionetworks.bridge.sdk.integration.Tests.setVariableValueInObject;
import static org.sagebionetworks.bridge.util.IntegTestUtils.TEST_APP_ID;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.rest.api.AssessmentsApi;
import org.sagebionetworks.bridge.rest.api.ForAdminsApi;
import org.sagebionetworks.bridge.rest.api.ForSuperadminsApi;
import org.sagebionetworks.bridge.rest.api.OrganizationsApi;
import org.sagebionetworks.bridge.rest.api.SharedAssessmentsApi;
import org.sagebionetworks.bridge.rest.api.TagsApi;
import org.sagebionetworks.bridge.rest.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.rest.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.rest.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.rest.model.Assessment;
import org.sagebionetworks.bridge.rest.model.AssessmentList;
import org.sagebionetworks.bridge.rest.model.ColorScheme;
import org.sagebionetworks.bridge.rest.model.Label;
import org.sagebionetworks.bridge.rest.model.PropertyInfo;
import org.sagebionetworks.bridge.rest.model.RequestParams;
import org.sagebionetworks.bridge.user.TestUserHelper;
import org.sagebionetworks.bridge.user.TestUserHelper.TestUser;

public class AssessmentTest {
    private static final ImmutableList<Label> LABELS = ImmutableList.of(new Label().lang("en").value("English"),
            new Label().lang("fr").value("French"));
    private static final ColorScheme COLOR_SCHEME = new ColorScheme()
            .background("#111111")
            .foreground("#222222")
            .activated("#333333")
            .inactivated("#444444");
    private static final String TITLE = "Title";
    private static final String TAG1 = "category:cat1";
    private static final String TAG2 = "category:cat2";
    
    // This isn't usable until the configuration is implemented, but 
    // verify it is persisted correctly
    private static final Map<String, List<PropertyInfo>> CUSTOMIZATION_FIELDS = ImmutableMap.of(
            "node1", ImmutableList.of(
                    new PropertyInfo().propName("field1").label("field1 label"), 
                    new PropertyInfo().propName("field2").label("field2 label")),
            "node2", ImmutableList.of(
                    new PropertyInfo().propName("field3").label("field3 label"), 
                    new PropertyInfo().propName("field4").label("field4 label")));

    private TestUser admin;
    private TestUser devOrg1;
    private TestUser devOrg2;
    private TestUser studyDesignerOrg1;
    private TestUser studyDesignerOrg2;
    private String id;
    private String markerTag;
    private OrganizationsApi orgsApi;
    private AssessmentsApi assessmentApiOrg1;
    private AssessmentsApi assessmentApiOrg2;
    
    @Before
    public void before() throws Exception {
        id = randomIdentifier(AssessmentTest.class);
        markerTag = "test:" + randomIdentifier(AssessmentTest.class);

        admin = TestUserHelper.getSignedInAdmin();
        orgsApi = admin.getClient(OrganizationsApi.class);
    }
    
    @After
    public void after() throws IOException {
        if (devOrg1 != null) {
            devOrg1.signOutAndDeleteUser();            
        }
        if (devOrg2 != null) {
            devOrg2.signOutAndDeleteUser();
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
        
        AssessmentList assessments = api.getAssessments(
                null, null, ImmutableList.of(markerTag), true).execute().body();
        for (Assessment oneAssessment : assessments.getItems()) {
            AssessmentList revisions = api.getAssessmentRevisionsById(
                    oneAssessment.getIdentifier(), null, null, true).execute().body();
            for (Assessment revision : revisions.getItems()) {
                api.deleteAssessment(revision.getGuid(), true).execute();
            }
        }
        AssessmentList sharedAssessments = sharedApi.getSharedAssessments(
                null, null, ImmutableList.of(markerTag), true).execute().body();
        for (Assessment oneSharedAssessment : sharedAssessments.getItems()) {
            AssessmentList revisions = sharedApi.getSharedAssessmentRevisionsById(
                    oneSharedAssessment.getIdentifier(), null, null, true).execute().body();
            for (Assessment revision : revisions.getItems()) {
                sharedApi.deleteSharedAssessment(revision.getGuid(), true).execute();
            }
        }
        TagsApi tagsApi = admin.getClient(TagsApi.class);
        tagsApi.deleteTag(TAG1).execute();
        tagsApi.deleteTag(TAG2).execute();
        tagsApi.deleteTag(markerTag).execute();
    }
    
    @Test
    public void testForDeveloper() throws Exception {
        devOrg1 = new TestUserHelper.Builder(AssessmentTest.class).withRoles(DEVELOPER).createAndSignInUser();
        orgsApi.addMember(ORG_ID_1, devOrg1.getUserId()).execute();
        
        devOrg2 = new TestUserHelper.Builder(AssessmentTest.class).withRoles(DEVELOPER).createAndSignInUser();
        orgsApi.addMember(ORG_ID_2, devOrg2.getUserId()).execute();
        
        assessmentApiOrg1 = devOrg1.getClient(AssessmentsApi.class);
        assessmentApiOrg2 = devOrg2.getClient(AssessmentsApi.class);
        
        // createAssessment works
        Assessment unsavedAssessment = new Assessment()
                .identifier(id)
                .title(TITLE)
                .summary("Summary")
                .validationStatus("Not validated")
                .normingStatus("Not normed")
                .osName("Both")
             // does not matter that org1 developer is creating this...they are a developer
                .ownerId(ORG_ID_2) 
                .minutesToComplete(15)
                .colorScheme(COLOR_SCHEME)
                .labels(LABELS)
                .tags(ImmutableList.of(markerTag, TAG1, TAG2))
                .customizationFields(CUSTOMIZATION_FIELDS);
        
        Assessment firstRevision = assessmentApiOrg1.createAssessment(unsavedAssessment).execute().body();
        assertFields(firstRevision, ORG_ID_2);
        
        // createAssessment fails when identifier already exists
        try {
            assessmentApiOrg1.createAssessment(unsavedAssessment).execute().body();
            fail("Should have thrown an exception");
        } catch(EntityAlreadyExistsException e) {
        }
        
        // getAssessmentByGUID works
        Assessment retValueByGuid = assessmentApiOrg1.getAssessmentByGUID(firstRevision.getGuid()).execute().body();
        assertEquals(firstRevision.getGuid(), retValueByGuid.getGuid());
        
        // getAssessmentById works
        Assessment retValueById = assessmentApiOrg1.getAssessmentById(id, firstRevision.getRevision()).execute().body();
        assertEquals(firstRevision.getGuid(), retValueById.getGuid());
        // verify fields
        assertFields(retValueById, ORG_ID_2);
        
        // createAssessmentRevision works
        firstRevision.setIdentifier(id);
        firstRevision.setOwnerId(ORG_ID_1);
        firstRevision.setTitle("Title 2");
        firstRevision.setRevision(2L);
        // Note: that the GUIDs here don't matter at all, which makes this an odd API. Should enforce this?
        Assessment secondRevision = assessmentApiOrg1.createAssessmentRevision(firstRevision.getGuid(), firstRevision).execute().body();
        
        // getAssessmentRevisionsByGUID works
        AssessmentList list = assessmentApiOrg1.getAssessmentRevisionsByGUID(secondRevision.getGuid(), null, null, false).execute().body();
        assertEquals(2, list.getItems().size());
        assertEquals(Integer.valueOf(2), list.getTotal());
        
        // getAssessmentRevisionsByGUID fails correctly when GUID not found
        try {
            assessmentApiOrg1.getAssessmentRevisionsByGUID("nonsense guid", null, null, false).execute();
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            assertEquals(e.getMessage(), "Assessment not found.");
        }
        
        // getLatestAssessmentRevision works
        Assessment latest = assessmentApiOrg1.getLatestAssessmentRevision(id).execute().body();
        assertEquals(secondRevision.getGuid(), latest.getGuid());
        assertEquals(secondRevision.getTitle(), latest.getTitle());
        assertEquals(ORG_ID_2, secondRevision.getOwnerId()); // can’t be changed on a revision
        
        // getAssessments works
        AssessmentList allAssessments = assessmentApiOrg1.getAssessments(
                0, 25, ImmutableList.of(markerTag), true).execute().body();
        assertEquals(1, allAssessments.getItems().size());
        assertEquals(Integer.valueOf(1), allAssessments.getTotal());
        assertEquals(secondRevision.getGuid(), allAssessments.getItems().get(0).getGuid());
        
        RequestParams rp = allAssessments.getRequestParams();
        assertEquals(Integer.valueOf(0), rp.getOffsetBy());
        assertEquals(Integer.valueOf(25), rp.getPageSize());
        assertEquals(ImmutableList.of(markerTag), rp.getTags());
        assertTrue(rp.isIncludeDeleted());
        
        // getAssessments works without tags
        allAssessments = assessmentApiOrg1.getAssessments(
                null, null, null, false).execute().body();
        assertTrue(allAssessments.getTotal() > 0);
        
        // getAssessments works with multiple tags
        allAssessments = assessmentApiOrg1.getAssessments(
                null, null, ImmutableList.of(markerTag, TAG1, TAG2), false).execute().body();
        assertEquals(1, allAssessments.getItems().size());
        assertEquals(Integer.valueOf(1), allAssessments.getTotal());
        assertEquals(secondRevision.getGuid(), allAssessments.getItems().get(0).getGuid());
        
        // updateAssessment works
        secondRevision.setTitle("Title 3");
        Assessment secondRevUpdated = assessmentApiOrg1.updateAssessment(secondRevision.getGuid(), secondRevision).execute().body();
        assertEquals(secondRevision.getTitle(), secondRevUpdated.getTitle());
        assertTrue(secondRevision.getVersion() < secondRevUpdated.getVersion());
        
        // updateAssessment succeeds for developers outside the owning organization
        secondRevUpdated.setSummary("This will be persisted");
        Assessment secondRevUpdatedAgain = assessmentApiOrg2.updateAssessment(secondRevUpdated.getGuid(), secondRevUpdated).execute().body();
        assertEquals("This will be persisted", secondRevUpdatedAgain.getSummary());
        
        // BUG: shared assessment with a revision lower than the highest revision in another app, with the 
        // same identifier, was not appearing in the API. Verify that this works before deleting one of the 
        // revisions.
        firstRevision = assessmentApiOrg1.publishAssessment(firstRevision.getGuid(), null).execute().body();
        
        SharedAssessmentsApi sharedApi = devOrg1.getClient(SharedAssessmentsApi.class);
        AssessmentList sharedList = sharedApi.getSharedAssessments(0, 50, null, null).execute().body();
        assertTrue(sharedList.getItems().stream().map(Assessment::getGuid)
                .collect(toSet()).contains(firstRevision.getOriginGuid()));
        
        // deleteAssessment physical=false works (even for developer who doesn't own the assessment)
        assessmentApiOrg2.deleteAssessment(secondRevision.getGuid(), false).execute();
        
        // now the first version is the latest
        latest = assessmentApiOrg1.getLatestAssessmentRevision(id).execute().body();
        assertEquals(firstRevision.getGuid(), latest.getGuid());
        assertEquals(TITLE, latest.getTitle());
        
        // getAssessmentRevisionsByGUID works
        allAssessments = assessmentApiOrg1.getAssessmentRevisionsByGUID(firstRevision.getGuid(), null, null, false).execute().body();
        assertEquals(1, allAssessments.getItems().size());
        assertEquals(Integer.valueOf(1), allAssessments.getTotal());
        assertEquals(firstRevision.getGuid(), allAssessments.getItems().get(0).getGuid());
        
        // getAssessmentRevisionsByGUID respects the logical delete flag
        allAssessments = assessmentApiOrg1.getAssessmentRevisionsByGUID(firstRevision.getGuid(), null, null, true).execute().body();
        assertEquals(2, allAssessments.getItems().size());
        assertEquals(Integer.valueOf(2), allAssessments.getTotal());
        
        // getAssessmentRevisionsById works
        allAssessments = assessmentApiOrg1.getAssessmentRevisionsById(id, 0, 50, false).execute().body();
        assertEquals(1, allAssessments.getItems().size());
        assertEquals(Integer.valueOf(1), allAssessments.getTotal());
        assertEquals(firstRevision.getGuid(), allAssessments.getItems().get(0).getGuid());
        
        // getAssessmentRevisionsById works for org2 member as well (not checking every single endpoint for this)
        allAssessments = assessmentApiOrg2.getAssessmentRevisionsById(id, 0, 50, false).execute().body();
        assertEquals(1, allAssessments.getItems().size());
        assertEquals(Integer.valueOf(1), allAssessments.getTotal());
        assertEquals(firstRevision.getGuid(), allAssessments.getItems().get(0).getGuid());
        
        // getAssessmentRevisionsById respects the logical delete flag
        allAssessments = assessmentApiOrg1.getAssessmentRevisionsById(id, 0, 50, true).execute().body();
        assertEquals(2, allAssessments.getItems().size());
        assertEquals(Integer.valueOf(2), allAssessments.getTotal());

        // getAssessmentRevisionsById bad offset just returns an empty list
        allAssessments = assessmentApiOrg1.getAssessmentRevisionsById(id, 20, 50, true).execute().body();
        assertEquals(0, allAssessments.getItems().size());
        assertEquals(Integer.valueOf(2), allAssessments.getTotal());
        
        // getAssessmentRevisionsById bad id throws an ENFE
        try {
            assessmentApiOrg1.getAssessmentRevisionsById("bad id", 0, 50, true).execute().body();
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        
        // SHARED ASSESSMENTS LIFECYCLE
        
        Assessment shared = sharedApi.getLatestSharedAssessmentRevision(id).execute().body();
        
        assertEquals(shared.getGuid(), firstRevision.getOriginGuid());
        assertEquals(TEST_APP_ID+":"+ORG_ID_2, shared.getOwnerId());
        
        assertNotEquals(firstRevision.getGuid(), shared.getGuid());
        assertEquals(firstRevision.getIdentifier(), shared.getIdentifier());

        Assessment sharedByGuid = sharedApi.getSharedAssessmentByGUID(
                shared.getGuid()).execute().body();
        assertEquals(sharedByGuid.getGuid(), shared.getGuid());
        
        Assessment sharedById = sharedApi.getSharedAssessmentById(
                shared.getIdentifier(), firstRevision.getRevision()).execute().body();
        assertEquals(sharedById.getGuid(), shared.getGuid());
        
        shared.setTitle("new title");
        shared.setSummary("new summary");
        Assessment sharedUpdated = sharedApi.updateSharedAssessment(shared.getGuid(), shared).execute().body();
        assertEquals(sharedUpdated.getTitle(), "new title");
        assertEquals(sharedUpdated.getSummary(), "new summary");
        
        // Make an assessment under the same identifier but a different owner... it cannot
        // be published back.
        Assessment otherAssessment = null;
        try {
            SharedAssessmentsApi sharedApiOrg2 = devOrg2.getClient(SharedAssessmentsApi.class);
            otherAssessment = sharedApiOrg2.importSharedAssessment(
                    shared.getGuid(), ORG_ID_1, null).execute().body();
            assessmentApiOrg2.publishAssessment(otherAssessment.getGuid(), null).execute();
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            assertTrue(e.getMessage().contains("Assessment exists in shared library under a different owner"));
        } finally {
            if (otherAssessment != null) {
                TestUser admin = TestUserHelper.getSignedInAdmin();
                admin.getClient(AssessmentsApi.class).deleteAssessment(otherAssessment.getGuid(), true).execute();
            }
        }
        
        TestUser admin = TestUserHelper.getSignedInAdmin();
        ForSuperadminsApi superAdminApi = admin.getClient(ForSuperadminsApi.class);
        SharedAssessmentsApi adminSharedApi = admin.getClient(SharedAssessmentsApi.class);

        // Import a shared assessment back into the app
        Assessment newAssessment = sharedApi.importSharedAssessment(shared.getGuid(), 
                ORG_ID_2, null).execute().body();        
        assertEquals(shared.getGuid(), newAssessment.getOriginGuid());
        assertEquals(ORG_ID_2, newAssessment.getOwnerId());
        assertNotEquals(shared.getGuid(), newAssessment.getGuid());
        // The revision of this thing should be 3 because there are two copies in app (one is logically 
        // deleted, but this does not break things)
        assertEquals(Long.valueOf(3L), newAssessment.getRevision());
        
        // deleteAssessment physical=true works
        admin.getClient(ForAdminsApi.class).deleteAssessment(secondRevision.getGuid(), true).execute();
        list = assessmentApiOrg1.getAssessments(
                null, null, ImmutableList.of(markerTag), true).execute().body();
        assertEquals(1, list.getItems().size());
        assertEquals(Integer.valueOf(1), list.getTotal());
        assertEquals(Long.valueOf(3L), list.getItems().get(0).getRevision());
        
        // clean up shared assessments. You have to delete dependent assessments first or it's
        // a ConstraintViolationException
        AssessmentList revisions = assessmentApiOrg1.getAssessmentRevisionsById(id, null, null, true).execute().body();
        AssessmentsApi adminAssessmentsApi = admin.getClient(AssessmentsApi.class);
        for (Assessment revision : revisions.getItems()) {
            adminAssessmentsApi.deleteAssessment(revision.getGuid(), true).execute();
        }
        try {
            superAdminApi.adminChangeApp(SHARED_SIGNIN).execute();
            // test logical delete of shared assessments
            adminSharedApi.deleteSharedAssessment(shared.getGuid(), false).execute().body();
            
            list = sharedApi.getSharedAssessments(null, null, ImmutableList.of(markerTag), false).execute().body();
            assertEquals(Integer.valueOf(0), list.getTotal());
            assertTrue(list.getItems().isEmpty());
            
            list = sharedApi.getSharedAssessments(null, null, ImmutableList.of(markerTag), true).execute().body();
            assertEquals(Integer.valueOf(1), list.getTotal());
            assertEquals(1, list.getItems().size());
            
            adminSharedApi.deleteSharedAssessment(shared.getGuid(), true).execute().body();
        } finally {
            superAdminApi.adminChangeApp(API_SIGNIN).execute();
        }
        // Should all be gone...
        list = sharedApi.getSharedAssessments(null, null, ImmutableList.of(markerTag), true).execute().body();
        assertTrue(list.getItems().isEmpty());
        
        // PAGING
        
        Set<String> uniqueGuids = new HashSet<>();
        
        // Test paging (10 records with different IDs)
        for (int i=0; i < 10; i++) {
            unsavedAssessment = new Assessment()
                    .identifier(id+i)
                    .title(TITLE)
                    .osName("Both")
                    .ownerId(ORG_ID_1)
                    .tags(ImmutableList.of(markerTag, TAG1, TAG2))
                    .customizationFields(CUSTOMIZATION_FIELDS);
            assessmentApiOrg1.createAssessment(unsavedAssessment).execute();
        }
        
        AssessmentList page1 = assessmentApiOrg1.getAssessments(0, 5, ImmutableList.of(markerTag), false).execute().body();
        assertEquals(Integer.valueOf(10), page1.getTotal());
        assertEquals(5, page1.getItems().size());
        assertEquals(id+"9", page1.getItems().get(0).getIdentifier());
        assertEquals(id+"8", page1.getItems().get(1).getIdentifier());
        assertEquals(id+"7", page1.getItems().get(2).getIdentifier());
        assertEquals(id+"6", page1.getItems().get(3).getIdentifier());
        assertEquals(id+"5", page1.getItems().get(4).getIdentifier());
        uniqueGuids.addAll(page1.getItems().stream().map(Assessment::getGuid).collect(toSet()));
        
        AssessmentList page2 = assessmentApiOrg1.getAssessments(5, 5, ImmutableList.of(markerTag), false).execute().body();
        assertEquals(Integer.valueOf(10), page2.getTotal());
        assertEquals(5, page2.getItems().size());
        assertEquals(id+"4", page2.getItems().get(0).getIdentifier());
        assertEquals(id+"3", page2.getItems().get(1).getIdentifier());
        assertEquals(id+"2", page2.getItems().get(2).getIdentifier());
        assertEquals(id+"1", page2.getItems().get(3).getIdentifier());
        assertEquals(id+"0", page2.getItems().get(4).getIdentifier());
        uniqueGuids.addAll(page2.getItems().stream().map(Assessment::getGuid).collect(toSet()));
        
        assertEquals(10, uniqueGuids.size());
        
        Set<String> uniqueRevisionGuidsById = new HashSet<>();
        Set<String> uniqueRevisionGuidsByGuid = new HashSet<>();
        
        // Test paging (10 revisions of the an assessment in the list of 10)
        String parentGuid = page1.getItems().get(0).getGuid();
        String parentId = page1.getItems().get(0).getIdentifier();
        unsavedAssessment = new Assessment()
                .identifier(parentId)
                .title(TITLE)
                .osName("Both")
                .ownerId(ORG_ID_1)
                .tags(ImmutableList.of(markerTag, TAG1, TAG2))
                .customizationFields(CUSTOMIZATION_FIELDS);
        for (int i=0; i < 10; i++) {
            unsavedAssessment.setRevision(Long.valueOf(i+2));
            assessmentApiOrg1.createAssessmentRevision(parentGuid, unsavedAssessment).execute().body();
        }
        
        AssessmentList page3 = assessmentApiOrg1.getAssessmentRevisionsById(parentId, 0, 10, true).execute().body();
        // 11 = the original, plus ten additional revisions
        assertRequestParams(page3, 0, 10, 11, true, 10);
        uniqueRevisionGuidsById.addAll(page3.getItems().stream()
                .map(Assessment::getGuid).collect(toSet()));
        
        AssessmentList page4 = assessmentApiOrg1.getAssessmentRevisionsById(parentId, 10, 10, true).execute().body();
        assertRequestParams(page4, 10, 10, 11, true, 1);
        uniqueRevisionGuidsById.addAll(page4.getItems().stream()
                .map(Assessment::getGuid).collect(toSet()));
        
        page3 = assessmentApiOrg1.getAssessmentRevisionsByGUID(parentGuid, 0, 10, true).execute().body();
        // 11 = the original, plus ten additional revisions
        assertRequestParams(page3, 0, 10, 11, true, 10);
        uniqueRevisionGuidsByGuid.addAll(page3.getItems().stream()
                .map(Assessment::getGuid).collect(toSet()));
        
        page4 = assessmentApiOrg1.getAssessmentRevisionsByGUID(parentGuid, 10, 10, true).execute().body();
        assertRequestParams(page4, 10, 10, 11, true, 1);
        uniqueRevisionGuidsByGuid.addAll(page4.getItems().stream()
                .map(Assessment::getGuid).collect(toSet()));
        
        // There are 11 revisions
        assertEquals(11, uniqueRevisionGuidsById.size());
        assertEquals(11, uniqueRevisionGuidsByGuid.size());
        assertEquals(uniqueRevisionGuidsById, uniqueRevisionGuidsByGuid);
        // so, only one overlapping item between the two sets (the most recent revision returned from 
        // both APIs).
        assertEquals(1, Sets.intersection(uniqueGuids, uniqueRevisionGuidsById).size());
        assertEquals(1, Sets.intersection(uniqueGuids, uniqueRevisionGuidsByGuid).size());
        
        // Publish all of these to shared folder so we can test shared assessment paging. We're 
        // publishing 10 distinct identifiers in one revision (page1 and pag2) and then we're 
        // publishing 10 revisions of one identifier (page3 and page4), so we can test all the 
        // shared paging APIs.
        
        Set<String> allGuids = new HashSet<>();
        allGuids.addAll(uniqueGuids);
        allGuids.addAll(uniqueRevisionGuidsById);
        
        // clear these to verify that the items from the shared APIs are unique
        uniqueGuids.clear();
        uniqueRevisionGuidsById.clear();
        uniqueRevisionGuidsByGuid.clear();
        
        for (String guid : allGuids) {
            assessmentApiOrg1.publishAssessment(guid, null).execute();
        }
        
        AssessmentList sharedPage1 = sharedApi.getSharedAssessments(
                0, 5, ImmutableList.of(markerTag), false).execute().body();
        assertRequestParams(sharedPage1, 0, 5, 10, false, 5);
        uniqueGuids.addAll(sharedPage1.getItems().stream()
                .map(Assessment::getGuid).collect(toSet()));
        
        AssessmentList sharedPage2 = sharedApi.getSharedAssessments(
                5, 5, ImmutableList.of(markerTag), false).execute().body();
        assertRequestParams(sharedPage2, 5, 5, 10, false, 5);
        uniqueGuids.addAll(sharedPage2.getItems().stream()
                .map(Assessment::getGuid).collect(toSet()));
        
        AssessmentList sharedPage3 = sharedApi.getSharedAssessmentRevisionsById(
                parentId, 0, 5, true).execute().body();
        assertRequestParams(sharedPage3, 0, 5, 11, true, 5);
        uniqueRevisionGuidsById.addAll(sharedPage3.getItems().stream()
                .map(Assessment::getGuid).collect(toSet()));
        
        AssessmentList sharedPage4 = sharedApi.getSharedAssessmentRevisionsById(
                parentId, 5, 5, true).execute().body();
        assertRequestParams(sharedPage4, 5, 5, 11, true, 5);
        uniqueRevisionGuidsById.addAll(sharedPage4.getItems().stream()
                .map(Assessment::getGuid).collect(toSet()));
        
        // get that one last item...
        AssessmentList sharedPage5 = sharedApi.getSharedAssessmentRevisionsById(
                parentId, 10, 5, true).execute().body();
        assertRequestParams(sharedPage5, 10, 5, 11, true, 1);
        uniqueRevisionGuidsById.addAll(sharedPage5.getItems().stream()
                .map(Assessment::getGuid).collect(toSet()));
        
        String sharedGuid = sharedPage3.getItems().get(0).getGuid();

        AssessmentList sharedPage6 = sharedApi.getSharedAssessmentRevisionsByGUID(
                sharedGuid, 0, 5, true).execute().body();
        assertRequestParams(sharedPage6, 0, 5, 11, true, 5);
        // Should not change the count, it's the same stuff...
        uniqueRevisionGuidsByGuid.addAll(sharedPage6.getItems().stream()
                .map(Assessment::getGuid).collect(toSet()));
        
        AssessmentList sharedPage7 = sharedApi.getSharedAssessmentRevisionsByGUID(
                sharedGuid, 5, 5, true).execute().body();
        assertRequestParams(sharedPage7, 5, 5, 11, true, 5);
        // Should not change the count, it's the same stuff...
        uniqueRevisionGuidsByGuid.addAll(sharedPage7.getItems().stream()
                .map(Assessment::getGuid).collect(toSet()));
        
        // get that one last item...
        AssessmentList sharedPage8 = sharedApi.getSharedAssessmentRevisionsByGUID(
                sharedGuid, 10, 5, true).execute().body();
        assertRequestParams(sharedPage8, 10, 5, 11, true, 1);
        uniqueRevisionGuidsByGuid.addAll(sharedPage8.getItems().stream()
                .map(Assessment::getGuid).collect(toSet()));
        
        assertEquals(10, uniqueGuids.size());
        assertEquals(11, uniqueRevisionGuidsById.size());
        assertEquals(11, uniqueRevisionGuidsByGuid.size());
    }
    
    // Study designers cannot work across the ownership boundary
    @Test
    public void testForStudyDesigner() throws Exception {
        studyDesignerOrg1 = new TestUserHelper.Builder(AssessmentTest.class).withRoles(STUDY_DESIGNER).createAndSignInUser();
        orgsApi.addMember(ORG_ID_1, studyDesignerOrg1.getUserId()).execute();
        
        studyDesignerOrg2 = new TestUserHelper.Builder(AssessmentTest.class).withRoles(STUDY_DESIGNER).createAndSignInUser();
        orgsApi.addMember(ORG_ID_2, studyDesignerOrg2.getUserId()).execute();
        
        assessmentApiOrg1 = studyDesignerOrg1.getClient(AssessmentsApi.class);
        assessmentApiOrg2 = studyDesignerOrg2.getClient(AssessmentsApi.class);
        
        // createAssessment works
        Assessment unsavedAssessment = new Assessment()
                .identifier(id)
                .title(TITLE)
                .summary("Summary")
                .validationStatus("Not validated")
                .normingStatus("Not normed")
                .osName("Both")
                .ownerId(ORG_ID_2) // this will be reset to org1, because the caller is in org1
                .minutesToComplete(15)
                .colorScheme(COLOR_SCHEME)
                .labels(LABELS)
                .tags(ImmutableList.of(markerTag, TAG1, TAG2))
                .customizationFields(CUSTOMIZATION_FIELDS);
        
        Assessment firstRevision = assessmentApiOrg1.createAssessment(unsavedAssessment).execute().body();
        assertFields(firstRevision, ORG_ID_1);
        
        // createAssessmentRevision works
        firstRevision.setIdentifier(id);
        firstRevision.setOwnerId(ORG_ID_2); // silently reset to org1
        firstRevision.setTitle("Title 2");
        firstRevision.setRevision(2L);
        // Note: that the GUIDs here don't matter at all, which makes this an odd API. Should enforce this?
        Assessment secondRevision = assessmentApiOrg1.createAssessmentRevision(firstRevision.getGuid(), firstRevision).execute().body();
        assertEquals(ORG_ID_1, secondRevision.getOwnerId());
        
        // The study designer in org2 cannot see this assessment
        AssessmentList list = assessmentApiOrg2.getAssessments(null, null, null, false).execute().body();
        assertFalse(list.getItems().stream().anyMatch(asmt -> asmt.getIdentifier().equals(id)));
        
        // Similarly the org2 study designer cannot update the assessment
        try {
            secondRevision.setSummary("This will be persisted");
            assessmentApiOrg2.updateAssessment(secondRevision.getGuid(), secondRevision).execute();
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
    }

    private void assertRequestParams(AssessmentList list, int offsetBy, int pageSize, int total,
            boolean includeDeleted, int actualSize) {
        assertEquals(Integer.valueOf(total), list.getTotal());
        assertEquals(Integer.valueOf(offsetBy), list.getRequestParams().getOffsetBy());
        assertEquals(Integer.valueOf(pageSize), list.getRequestParams().getPageSize());
        assertEquals(includeDeleted, list.getRequestParams().isIncludeDeleted());
        assertEquals(actualSize, list.getItems().size());
        // all the GUIDs are unique.
        Set<String> guids = list.getItems().stream().map(Assessment::getGuid).collect(toSet());
        assertEquals(actualSize, guids.size());
    }
    
    private void assertFields(Assessment assessment, String orgId) throws Exception {
        // null all these out so equality works
        setVariableValueInObject(assessment.getColorScheme(), "type", null);
        setVariableValueInObject(assessment.getLabels().get(0), "type", null);
        setVariableValueInObject(assessment.getLabels().get(1), "type", null);
        
        assertEquals(id, assessment.getIdentifier());
        assertEquals(TITLE, assessment.getTitle());
        assertEquals("Summary", assessment.getSummary());
        assertEquals("Not validated", assessment.getValidationStatus());
        assertEquals("Not normed", assessment.getNormingStatus());
        assertEquals("Universal", assessment.getOsName());
        assertEquals(orgId, assessment.getOwnerId());
        assertEquals(new Integer(15), assessment.getMinutesToComplete());
        assertEquals(COLOR_SCHEME, assessment.getColorScheme());
        assertEquals(LABELS, assessment.getLabels());
        assertTrue(assessment.getTags().contains(markerTag));
        assertTrue(assessment.getTags().contains(TAG1));
        assertTrue(assessment.getTags().contains(TAG2));
        List<PropertyInfo> node1Props = assessment.getCustomizationFields().get("node1");
        List<PropertyInfo> node2Props = assessment.getCustomizationFields().get("node2");
        assertEquals(ImmutableSet.of("field1", "field2"), 
                node1Props.stream().map(PropertyInfo::getPropName).collect(toSet()));
        assertEquals(ImmutableSet.of("field3", "field4"), 
                node2Props.stream().map(PropertyInfo::getPropName).collect(toSet()));
        assertEquals(Long.valueOf(1), assessment.getRevision());
        assertEquals(Long.valueOf(1L), assessment.getVersion());
    }
}
