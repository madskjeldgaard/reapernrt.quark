ReaperNRT {
  classvar <args, <paths, <server, <outFileName, <inputFile;

  *synthFunc{|numChannels|
    ^{|dustFreq=10|
      var in = SoundIn.ar([0]),
      fft = FFT(LocalBuf(1024, 1), in);
      fft = PV_MagFreeze(fft, ToggleFF.kr(Dust.kr(dustFreq)));
      Out.ar(0, IFFT(fft).dup)
    }
  }

  *serverOptions{|numChannels|
    ^ServerOptions.new
      .numOutputBusChannels_(numChannels)
      .maxSynthDefs_(100000)
      .numInputBusChannels_(numChannels)
  }

  *synthArgs{
    ^[\dustFreq, 12]
  }

  /* ------------------ */
  /* Private methods    */
  /* ------------------ */

  *new {
    ^this.prInit();
  }

  *prInit {
    // Command line arguments
    args = thisProcess.argv;

    // Paths of sound files to be processed
    paths = args.collect{|argument|
      PathName.new(argument.asString)
    };

    fork{
      if(paths.size == 0, {"No argument supplied for path".error; 1.exit}, {
        paths.do{|path, index|
          if(path.isFile, {
            this.prProcess(path)
            }, {
              1.exit
            });
        };

        0.exit

      });
    }

  }

  *prProcess{|pathName|

    var score;
    var cond = Condition.new;
    inputFile = SoundFile.openRead(pathName.fullPath);
	inputFile.close;  // doesn't need to stay open; we just need the stats

    server = Server(\nrt, options: this.serverOptions(inputFile.numChannels));

    // @TODO make unique
    outFileName = "%%_nrtprocessed.%".format(
      pathName.pathOnly, pathName.fileNameWithoutExtension, pathName.extension
    ).standardizePath;

    score = Score([
      [0.0, ['/d_recv',
      SynthDef(\soundprocessor, this.synthFunc(inputFile.numChannels)).asBytes
      ]],
      [0.0, Synth.basicNew(\soundprocessor, server).newMsg(args: this.synthArgs())]
      ]);

    score.recordNRT(
      outputFilePath: outFileName,
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
    "Done processing % using %".format(pathName.fileName, this.name).postln;

    server.remove;

  }

}
