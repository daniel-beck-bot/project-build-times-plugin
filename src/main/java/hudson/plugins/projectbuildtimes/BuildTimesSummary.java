package hudson.plugins.projectbuildtimes;

import java.util.ArrayList;
import java.util.List;
import hudson.plugins.projectbuildtimes.BuildTimes;

public class BuildTimesSummary extends BuildTimes {

   private List<BuildTimes> buildTimes = new ArrayList<BuildTimes>();

   public BuildTimesSummary() {
      super(null, 0);
   }

   public BuildTimesSummary addBuildTimes(BuildTimes buildTime) {
      buildTimes.add(buildTime);
      this.buildTime += buildTime.getBuildTime();
      return this;
   }

   public List<BuildTimes> getBuildTimes() {
      return buildTimes;
   }
}
