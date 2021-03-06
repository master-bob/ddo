package org.sakaiproject.ddo.tool.pages;

import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.image.ContextImage;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.navigation.paging.PagingNavigator;
import org.apache.wicket.markup.repeater.DefaultItemReuseStrategy;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.IDataProvider;
import org.apache.wicket.model.*;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.wicket.request.handler.resource.ResourceStreamRequestHandler;
import org.apache.wicket.request.resource.ContextRelativeResource;
import org.apache.wicket.util.resource.AbstractResourceStreamWriter;
import org.sakaiproject.ddo.logic.SakaiProxy;
import org.sakaiproject.ddo.model.Feedback;
import org.sakaiproject.ddo.model.Submission;
import org.sakaiproject.ddo.model.SubmissionFile;

/**
 * Created by David P. Bauer on 12/10/14.
 */
public class StudentOverview extends BasePage {

    Link<Void> toDropOffFormLink;
    SubmissionDataProvider provider;

    public StudentOverview() {
        disableLink(studentOverviewLink);

        //link to drop off form
        //the i18n label for this is directly in the HTML
        toDropOffFormLink = new Link<Void>("toDropOffFormLink") {
            private static final long serialVersionUID = 1L;
            public void onClick() {
                setResponsePage(new DropOffForm());
            }
        };
        add(toDropOffFormLink);

        //get list of items from db, wrapped in a dataprovider
        provider = new SubmissionDataProvider();

        //present the data in a table
        final DataView<Submission> dataView = new DataView<Submission>("simple", provider) {

            @Override
            public void populateItem(final Item item) {

                DateFormat df = new SimpleDateFormat("MMM d, yyyy h:mm a");

                final Submission submission = (Submission) item.getModelObject();

                Link<Void> feedback;
                Label feedbackLabel;
                final List<Feedback> feedbackList = projectLogic.getFeedbackForSubmission(submission.getSubmissionId());
                if (feedbackList != null && !feedbackList.isEmpty()) {
                    final long feedbackId = feedbackList.get(0).getFeedbackId();
                    feedback = new Link<Void>("feedback") {
                        @Override
                        public void onClick() {
                            setResponsePage(new FeedbackPage(feedbackId,"student"));
                        }
                    };
                    feedbackLabel = new Label("feedbackLabel",new ResourceModel("link.view_feedback"));
                } else {
                    feedback = new Link<Void>("feedback"){
                        @Override
                        public void onClick() {}
                    };
                    feedbackLabel = new Label("feedbackLabel","");
                }
                feedback.add(feedbackLabel);
                item.add(feedback);

                Link<Void> streamDownloadLink = new Link<Void>("document") {

                    @Override
                    public void onClick() {

                        AbstractResourceStreamWriter rstream = new AbstractResourceStreamWriter() {

                            @Override
                            public void write(OutputStream output) throws IOException {
                                output.write(sakaiProxy.getResource(submission.getDocumentRef()).getBytes());
                            }
                        };

                        ResourceStreamRequestHandler handler = new ResourceStreamRequestHandler(rstream, sakaiProxy.getResource(submission.getDocumentRef()).getFileName());
                        getRequestCycle().scheduleRequestHandlerAfterCurrent(handler);
                    }
                };

                item.add(streamDownloadLink);
                SubmissionFile sf = sakaiProxy.getResource(submission.getDocumentRef());
                streamDownloadLink.add(new Label("fileName", sf==null?"Cannot find file":sf.getFileName()));
                item.add(new ContextImage("submissionIcon", new Model<String>(sakaiProxy.getResourceIconUrl(submission.getDocumentRef()))));
                item.add(new Label("fileSize", sakaiProxy.getResourceFileSize(submission.getDocumentRef())));
                item.add(new Label("submissiondate", df.format(submission.getSubmissionDate())));
                item.add(new Label("status", submission.getStatus()));
            }
        };
        dataView.setItemReuseStrategy(new DefaultItemReuseStrategy());
        dataView.setItemsPerPage(15);
        add(dataView);

        //add a pager to our table, only visible if we have more than 5 items
        add(new PagingNavigator("navigator", dataView) {

            @Override
            public boolean isVisible() {
                if(provider.size() > 15) {
                    return true;
                }
                return false;
            }

            @Override
            public void onBeforeRender() {
                super.onBeforeRender();

                //clear the feedback panel messages
                clearFeedback(feedbackPanel);
            }
        });

        add(new Label("numberOfSubmissions", provider.size()));

        WebMarkupContainer submissionQueueInfo = new WebMarkupContainer("submissionQueueInfo");

        int numberOfWaitingSubmissions = projectLogic.getNumberOfWaitingSubmissions();

        submissionQueueInfo.add(new Label("numberWaitingReview", String.valueOf(numberOfWaitingSubmissions)));
        submissionQueueInfo.add(new Label("expectedWaitTime", getExpectedWaitTime(numberOfWaitingSubmissions)));
        submissionQueueInfo.add(new Label("expectedReturnDay", getExpectedReturnDay(numberOfWaitingSubmissions)));

        add(submissionQueueInfo);
    }

    private String getExpectedWaitTime(int numberOfWaitingSubmissions) {
        if(numberOfWaitingSubmissions < 9) {
            return "48 hours";
        } else if (numberOfWaitingSubmissions < 18) {
            return "72 hours";
        } else {
            return "96 hours";
        }
    }

    private String getExpectedReturnDay(int numberOfWaitingSubmissions) {
        Calendar today = Calendar.getInstance();
        int currentDayOfWeek = today.get(Calendar.DAY_OF_WEEK);
        int expectedReturnDay;

        if(numberOfWaitingSubmissions < 9) {
            expectedReturnDay = currentDayOfWeek + 2;
        } else if (numberOfWaitingSubmissions < 18) {
            expectedReturnDay = currentDayOfWeek + 3;
        } else {
            expectedReturnDay = currentDayOfWeek + 4;
        }
        if(expectedReturnDay > 7)
            expectedReturnDay = expectedReturnDay - 7;

        String dayOfWeek = "";

        switch(expectedReturnDay){
            case 1:
                dayOfWeek="Sunday";
                break;
            case 2:
                dayOfWeek="Monday";
                break;
            case 3:
                dayOfWeek="Tuesday";
                break;
            case 4:
                dayOfWeek="Wednesday";
                break;
            case 5:
                dayOfWeek="Thursday";
                break;
            case 6:
                dayOfWeek="Friday";
                break;
            case 7:
                dayOfWeek="Saturday";
                break;
        }

        return dayOfWeek;
    }

    /**
     * DataProvider to manage our list
     *
     */
    private class SubmissionDataProvider implements IDataProvider<Submission> {

        private List<Submission> list;

        private List<Submission> getData() {
            if(list == null) {
                list = projectLogic.getSubmissionsForUser(sakaiProxy.getCurrentUserId());
                Collections.sort(list, new Comparator<Submission>() {
                    @Override
                    public int compare(Submission s1, Submission s2) {
                        long t1 = s1.getSubmissionDate().getTime();
                        long t2 = s2.getSubmissionDate().getTime();
                        if(t1 < t2) return -1;
                        else if (t1 > t2) return 1;
                        else return 0;
                    }
                });
                Collections.reverse(list);
            }
            return list;
        }


        @Override
        public Iterator<Submission> iterator(long first, long count){
            int f = (int) first; //not ideal but ok for demo
            int c = (int) count; //not ideal but ok for demo
            return getData().subList(f, f + c).iterator();
        }

        @Override
        public long size(){
            return getData().size();
        }

        @Override
        public IModel<Submission> model(Submission object){
            return new DetachableSubmissionModel(object);
        }

        @Override
        public void detach(){
            list = null;
        }
    }

    /**
     * Detachable model to wrap a Submission
     *
     */
    private class DetachableSubmissionModel extends LoadableDetachableModel<Submission> {

        private final long id;

        /**
         * @param s
         */
        public DetachableSubmissionModel(Submission s){
            this.id = s.getSubmissionId();
        }

        /**
         * @param id
         */
        public DetachableSubmissionModel(long id){
            this.id = id;
        }

        /**
         * @see java.lang.Object#hashCode()
         */
        public int hashCode() {
            return Long.valueOf(id).hashCode();
        }

        /**
         * used for dataview with ReuseIfModelsEqualStrategy item reuse strategy
         *
         * @see org.apache.wicket.markup.repeater.ReuseIfModelsEqualStrategy
         * @see java.lang.Object#equals(java.lang.Object)
         */
        public boolean equals(final Object obj){
            if (obj == this){
                return true;
            }
            else if (obj == null){
                return false;
            }
            else if (obj instanceof DetachableSubmissionModel) {
                DetachableSubmissionModel other = (DetachableSubmissionModel)obj;
                return other.id == id;
            }
            return false;
        }

        /**
         * @see org.apache.wicket.model.LoadableDetachableModel#load()
         */
        protected Submission load(){

            // get the submission
            return projectLogic.getSubmission(id);
        }
    }
}
