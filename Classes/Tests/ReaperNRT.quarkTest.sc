ReaperNRT.quarkTest1 : UnitTest {
	test_check_classname {
		var result = ReaperNRT.quark.new;
		this.assert(result.class == ReaperNRT.quark);
	}
}


ReaperNRT.quarkTester {
	*new {
		^super.new.init();
	}

	init {
		ReaperNRT.quarkTest1.run;
	}
}
