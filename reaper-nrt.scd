(
var args = thisProcess.argv;
var paths = args.collect{|argument|
	argument.class.postln;
	PathName.new(argument.postln.asString)
};
var processFile = {|filePath|
	var server;
	var pathName = filePath;
	var inputFile = SoundFile.openRead(filePath.fullPath);
	var cond = Condition.new;
	var synthfunc;
	inputFile.close;  // doesn't need to stay open; we just need the stats

	server = Server(\nrt,
		options: ServerOptions.new
		.numOutputBusChannels_(2)
		.maxSynthDefs_(100000)
		.numInputBusChannels_(inputFile.numChannels)
	);

	synthfunc = {
		var in = SoundIn.ar([0]),
		fft = FFT(LocalBuf(1024, 1), in);
		fft = PV_MagFreeze(fft, ToggleFF.kr(Dust.kr(12)));
		Out.ar(0, IFFT(fft).dup)
	};

	x = Score([
		[0.0, ['/d_recv',
			SynthDef(\soundprocessor, synthfunc).asBytes
		]],
		[0.0, Synth.basicNew(\soundprocessor, server).newMsg]
	]);

	x.recordNRT(
		outputFilePath: "%%_nrtprocessed.%".format(
			pathName.pathOnly, pathName.fileNameWithoutExtension, pathName.extension
		).standardizePath.postln,
		inputFilePath: inputFile.path,
		sampleRate: inputFile.sampleRate,
		headerFormat: inputFile.headerFormat,
		sampleFormat: inputFile.sampleFormat,
		options: server.options,
		duration: inputFile.duration,
		action: {
			cond.unhang()
		}
	);

	cond.hang();
	"Done processing %".format(filePath.fileName).postln;

	server.remove;
};

fork{
	if(paths.size == 0, {"No argument supplied for path".error; 1.exit}, {
		paths.do{|path, index|
			if(path.isFile, {
				processFile.value(path);
			}, {
				1.exit
			});
		};

		0.exit

	});
}
)
