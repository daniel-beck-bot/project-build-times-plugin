package hudson.plugins.projectbuildtimes;

import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import java.util.List;

/**
 *
 * @author Neil Rhine
 */
public class BuildTimes {

  private Job job;
  long buildTime;

  public BuildTimes(Job job, long buildTime) {
      super();
      this.job = job;
      this.buildTime = buildTime;
   }

   public Job getJob() {
      return job;
   }

   public long getBuildTime() {
      return buildTime;
   }

   // Return in Seconds for readability
   public long getBuildTimeInSeconds() {
      long secondsDivisor = 1000l;
      long buildTimeSec = buildTime/secondsDivisor;
      return buildTimeSec;
   }

}
