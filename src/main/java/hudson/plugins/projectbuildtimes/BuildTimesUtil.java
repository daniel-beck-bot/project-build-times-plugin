package hudson.plugins.projectbuildtimes;

import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import hudson.plugins.projectbuildtimes.BuildTimes;

import java.util.Collection;

public class BuildTimesUtil {

   public static BuildTimes getBuildTimes(Run run) {
      long lastBuildTime = run.getDuration();
      return new BuildTimes(run.getParent(), lastBuildTime);
   }
}
