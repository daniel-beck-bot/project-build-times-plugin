package hudson.plugins.projectbuildtimes;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Run;
import hudson.util.ColorPalette;
import hudson.util.DataSetBuilder;
import hudson.util.Graph;
import hudson.util.ShiftedCategoryAxis;
import hudson.plugins.view.dashboard.DashboardPortlet;

import java.awt.Color;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.AreaRenderer;
import org.jfree.chart.renderer.category.StackedAreaRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.ui.RectangleInsets;
import org.joda.time.LocalDate;
import org.joda.time.chrono.GregorianChronology;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.util.EnumConverter;
import hudson.plugins.projectbuildtimes.Messages;
import hudson.plugins.projectbuildtimes.BuildTimes;
import hudson.plugins.projectbuildtimes.BuildTimesSummary;
import hudson.plugins.projectbuildtimes.BuildTimesUtil;
import hudson.plugins.projectbuildtimes.LocalDateLabel;

public class BuildTimesChart extends DashboardPortlet {

   private int graphWidth = 300;
   private int graphHeight = 220;
   private int dateRange = 365;
   private int dateShift = 0;
   private boolean stacked = false;
   private String jenkinsJobNames = "";
   private List<Job> jobList = getDashboard().getJobs();

   private List<Color> colorList = Arrays.asList(ColorPalette.RED, ColorPalette.YELLOW, ColorPalette.BLUE);


   @DataBoundConstructor
   public BuildTimesChart(String name, int graphWidth, int graphHeight,
           String display, int dateRange, int dateShift, boolean stacked, String jenkinsJobNames) {
      super(name);
      this.graphWidth = graphWidth;
      this.graphHeight = graphHeight;
      this.dateRange = dateRange;
      this.dateShift = dateShift;
      this.stacked = stacked;
      this.jenkinsJobNames = jenkinsJobNames;
      if (!jenkinsJobNames.equals("")) {
         overwriteJobList();
      }
   }

   public void overwriteJobList() {
      List<Job> finalJobList = new ArrayList<Job>();
      String[] jobStrings = jenkinsJobNames.split(",");
      for(String jobName : jobStrings) {
         for(Job job: jobList){
            if(job.getDisplayName().equals(jobName)) {
               finalJobList.add(job);
               break;
            }
         }
      }
      jobList = finalJobList;
   }

   public int getDateRange() {
      return dateRange;
   }
   
   public int getDateShift() {
       return dateShift;
   }

   public int getGraphWidth() {
      return graphWidth <= 0 ? 300 : graphWidth;
   }

   public int getGraphHeight() {
      return graphHeight <= 0 ? 220 : graphHeight;
   }

   public boolean getStacked() {
      return stacked;
   }

   public String getJenkinsJobNames() {
      return jenkinsJobNames;
   }

   /**
    * Graph of duration of tests over time.
    */
   public Graph getSummaryGraph() {
      // The standard equals doesn't work because two LocalDate objects can
      // be differente even if the date is the same (different internal
      // timestamp)
      Comparator<LocalDate> localDateComparator = new Comparator<LocalDate>() {

         @Override
         public int compare(LocalDate d1, LocalDate d2) {
            if (d1.isEqual(d2)) {
               return 0;
            }
            if (d1.isAfter(d2)) {
               return 1;
            }
            return -1;
         }
      };

      final Map<String, Map<LocalDate, BuildTimesSummary>> summaryMap = new TreeMap<String, Map<LocalDate, BuildTimesSummary>>();

      LocalDate today = new LocalDate(System.currentTimeMillis() - dateShift*6000, GregorianChronology.getInstanceUTC());

      // for each job, for each day, add last build of the day to summary
      for (Job job : getDashboard().getJobs()) {
         // We need a custom comparator for LocalDate objects
         final Map<LocalDate, BuildTimesSummary> summaries = // new
              // HashMap<LocalDate,
              // BuildTimes>();
              new TreeMap<LocalDate, BuildTimesSummary>(localDateComparator);
         Run run = job.getFirstBuild();

         if (run != null) { // execute only if job has builds
             LocalDate runDay = new LocalDate(
                         run.getTimeInMillis() - dateShift*60000, GregorianChronology.getInstanceUTC());
             LocalDate firstDay = (dateRange != 0) ? new LocalDate(
            		 System.currentTimeMillis() - dateShift*6000, GregorianChronology.getInstanceUTC()).minusDays(dateRange) : runDay;

            while (run != null) {
               runDay = new LocalDate(
            		 run.getTimeInMillis() - dateShift*60000, GregorianChronology.getInstanceUTC());
               Run nextRun = run.getNextBuild();

               if (nextRun != null) {
                  LocalDate nextRunDay = new LocalDate(
                		  nextRun.getTimeInMillis() - dateShift*60000, GregorianChronology.getInstanceUTC());
                  // skip run before firstDay, but keep if next build is
                  // after start date
                  if (!runDay.isBefore(firstDay)
                          || runDay.isBefore(firstDay)
                          && !nextRunDay.isBefore(firstDay)) {
                     // if next run is not the same day, use this test to
                     // summarize
                     if (nextRunDay.isAfter(runDay)) {
                        summarize(summaries, run,
                                (runDay.isBefore(firstDay) ? firstDay
                                : runDay),
                                nextRunDay.minusDays(1));
                     }
                  }
               } else {
                  // use this run's test result from last run to today
                  summarize(
                          summaries,
                          run,
                          (runDay.isBefore(firstDay) ? firstDay : runDay),
                          today);
               }

               run = nextRun;
            }
         }
         summaryMap.put(job.getDisplayName(), summaries);
      }
      if (stacked) {
         return createStackedGraph(summaryMap);
      } else {
         return createLineGraph(summaryMap);
      }
   }

   public Graph createStackedGraph(final Map<String, Map<LocalDate, BuildTimesSummary>> summaryMap) {

      return new Graph(-1, getGraphWidth(), getGraphHeight()) {

         @Override
         protected JFreeChart createGraph() {
            final JFreeChart chart = ChartFactory.createStackedAreaChart(
                    null, // chart title
                    Messages.BuildStats_Date(), // unused
                    Messages.BuildStats_Count(), // range axis label
                    buildDataSet(summaryMap), // data
                    PlotOrientation.VERTICAL, // orientation
                    false, // include legend
                    false, // tooltips
                    false // urls
                 );

            chart.setBackgroundPaint(Color.white);
   
            final CategoryPlot plot = chart.getCategoryPlot();

            plot.setBackgroundPaint(Color.WHITE);
            plot.setOutlinePaint(null);
            plot.setForegroundAlpha(0.8f);
            plot.setRangeGridlinesVisible(true);
            plot.setRangeGridlinePaint(Color.black);

            CategoryAxis domainAxis = new ShiftedCategoryAxis(null);
            plot.setDomainAxis(domainAxis);
            domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
            domainAxis.setLowerMargin(0.0);
            domainAxis.setUpperMargin(0.0);
            domainAxis.setCategoryMargin(0.0);

            final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
            rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

            int numSeries = plot.getDataset().getRowCount();

            StackedAreaRenderer ar = new StackedAreaRenderer();
            plot.setRenderer(ar);
   
            for(int i = 0; i<numSeries ;i++) {
               ar.setSeriesPaint(i, colorList.get(i));
            }

            return chart;
         }
      };
   }


   public Graph createLineGraph(final Map<String, Map<LocalDate, BuildTimesSummary>> summaryMap) {

      return new Graph(-1, getGraphWidth(), getGraphHeight()) {

         @Override
         protected JFreeChart createGraph() {
            final JFreeChart chart = ChartFactory.createLineChart(
                    null, // chart title
                    Messages.BuildStats_Date(), // unused
                    Messages.BuildStats_Count(), // range axis label
                    buildDataSet(summaryMap), // data
                    PlotOrientation.VERTICAL, // orientation
                    false, // include legend
                    false, // tooltips
                    false // urls
                 );

            chart.setBackgroundPaint(Color.white);
   
            final CategoryPlot plot = chart.getCategoryPlot();

            plot.setBackgroundPaint(Color.WHITE);
            plot.setOutlinePaint(null);
            plot.setForegroundAlpha(0.8f);
            plot.setRangeGridlinesVisible(true);
            plot.setRangeGridlinePaint(Color.black);

            CategoryAxis domainAxis = new ShiftedCategoryAxis(null);
            plot.setDomainAxis(domainAxis);
            domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
            domainAxis.setLowerMargin(0.0);
            domainAxis.setUpperMargin(0.0);
            domainAxis.setCategoryMargin(0.0);

            final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
            rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

            return chart;
         }
      };
   }

   private CategoryDataset buildDataSet(Map<String, Map<LocalDate, BuildTimesSummary>> summaryMap) {
      DataSetBuilder<String, LocalDateLabel> dsb = new DataSetBuilder<String, LocalDateLabel>();

      LocalDate today = new LocalDate(System.currentTimeMillis() - dateShift*6000, GregorianChronology.getInstanceUTC());

      for (Map.Entry<String, Map<LocalDate, BuildTimesSummary>> summaries : summaryMap.entrySet()) {
         for (Map.Entry<LocalDate, BuildTimesSummary> entry : summaries.getValue().entrySet()) {
            LocalDateLabel label = new LocalDateLabel(entry.getKey());
            dsb.add(entry.getValue().getBuildTimeInSeconds(), summaries.getKey(), label);
         }
      }
      return dsb.build();
   }

   private void summarize(Map<LocalDate, BuildTimesSummary> summaries, Run run, LocalDate firstDay, LocalDate lastDay) {
      BuildTimes buildTime = BuildTimesUtil.getBuildTimes(run);

      // for every day between first day and last day inclusive
      for (LocalDate curr = firstDay; curr.compareTo(lastDay) <= 0; curr = curr.plusDays(1)) {
         if (buildTime.getBuildTime() != 0) {
            BuildTimesSummary currBuildTimes = summaries.get(curr);
            if (currBuildTimes == null) {
               currBuildTimes = new BuildTimesSummary();
               summaries.put(curr, currBuildTimes);
            }
            if (curr.compareTo(firstDay) == 0) {
               currBuildTimes.addBuildTimes(buildTime);
            }
         }
      }
   }

   @Extension
   public static class DescriptorImpl extends Descriptor<DashboardPortlet> {

      @Override
      public String getDisplayName() {
         return Messages.BuildStats_BuildTimesChart();
      }
   }
}
