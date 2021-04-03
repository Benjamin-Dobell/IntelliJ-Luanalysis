import org.apache.tools.ant.taskdefs.condition.Os

rootProject.name = "Luanalysis"

if (Os.isFamily(Os.FAMILY_WINDOWS)) {
    include("debugger:attach:windows")
}
