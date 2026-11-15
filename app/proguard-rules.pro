# Room 2.2.5 keeps the generated WorkManager database class name, but its
# consumer rule omits the no-argument constructor called by Room.getGeneratedImplementation.
# R8 full mode removes that constructor unless it is explicitly retained.
-keepclassmembers class androidx.work.impl.WorkDatabase_Impl {
    public <init>();
}
