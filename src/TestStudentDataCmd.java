import static com.lexicalscope.jewel.cli.CliFactory.createCli;

public class TestStudentDataCmd extends CmdWithEdoz<CmdWithEdoz.Args> {

    public TestStudentDataCmd(String[] rawArgs) throws Exception {
		super(createCli(Args.class).parseArguments(rawArgs));
	}

	@Override
	void call() throws Exception {
	    var complete = students.stream().filter(s -> s.nethz.isPresent()).count();
		System.out.printf("Done. We have the NETHZ for %d/%d students\n",
		        complete, students.size());
	}
}
