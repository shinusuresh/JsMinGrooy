import static groovy.io.FileType.FILES

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Minification {	
	def regBuildPatternMulti = Pattern.compile("<!--\\s*build:(\\w+)(?:\\(([^\\)]+)\\))?\\s*([^\\s]+)\\s*-->(.*?)<!--\\s*endbuild\\s*-->", Pattern.DOTALL);
	def regendPattern = /<!--\s*endbuild\s*-->/;
		
	def directory = ""
	def ln = "";
	def compressApi = 'yui'
	
	Minification(def dir, def lineseperator, def compressor){
		directory = dir
		ln = lineseperator;
		compressApi = compressor
		println "Inside constructor set directory as "+dir
		processJsps();
	}
	
	def processJsps () { 
	
			println "Processing jsp's inside directory "+ this.directory
			def jspdir = new File(this.directory);
			jspdir.eachFileRecurse(FILES) {
				if(it.name.endsWith('.jsp') || it.name.endsWith('.jspf')) {
					println "Processing file "+it
					
					def jspFile = it.text					
					def v = jspFile =~ regBuildPatternMulti
					def captured	
					
					def jsInDebugMode
					
					def jsInMinifyMode
					
					def capturedjs = []
					def assetpath
					while(v.find()) {
						captured = v.group(0)
						//jsInDebugMode = v.group(0)
						println '[0]' +v.group(0)
						println '[1]' +v.group(1)
						println '[2]' +v.group(2)
						jsInMinifyMode = v.group(3)
						jsInDebugMode = v.group(4)						
						v.group(4).eachLine { line ->
							if(line) {
								capturedjs << line.trim()
							}							
						}
					}
					
					//This is the place we identified that a jsp/f file contains code which are to be processed
					if(captured) {
						def combinedJsFileNameIndex = jsInMinifyMode.lastIndexOf('.');
						def jsInCombineMode = this.directory+'/'+jsInMinifyMode.substring(0, combinedJsFileNameIndex)+'.combined.js'
						println 'jsInCombineMode '+jsInCombineMode
						def jspDebug = """\
								<c:choose>
									<c:when test="\${true eq param.jsdebug}">
										${jsInDebugMode}			
									</c:when>
									<c:when test="\${true eq param.jscombined}">
										<script type="text/javascript" src=\"${jsInCombineMode}\"></script>			
									</c:when>
									<c:otherwise>
										<script type="text/javascript" src=\"${ this.directory+'/'+jsInMinifyMode}\"></script>	
									</c:otherwise>
								</c:choose>
									"""
						
						def replace = it.text.replaceAll(captured){ all ->
							println 'Replacing...'
							"\n $jspDebug"
						}
						//println replace
						println jsInDebugMode
						println jsInMinifyMode
						println capturedjs
						//Create new file with replaced contents
						def file = new File(it.getAbsolutePath())
						def w = file.newWriter()
						w << replace
						w.close()
						combineAndMinifyjs(capturedjs, jsInMinifyMode)
					}//captured
									
				}//end jsp|jspf check
				
			}//eachFileRecurse			
	}
	
	def combineAndMinifyjs(js, outputfile) {
		def scriptPattern = /(href|src)=["']([^'"]+)["']/
		def jsToCombine = []
		js.eachWithIndex { src, i ->
			//println i + ' script is --> ' + src
			def scriptmatcher = src =~ scriptPattern	
			if(scriptmatcher) {
				scriptmatcher[0].eachWithIndex { obj, j ->
					//println j + " is " + obj
					if(j == 2) {
						println 'Js to load is '+obj
						jsToCombine.push(obj)
					}
				}
			}//scriptmatcher							
		}
		
		def combinedfile
		//Get files and combine
		def index = outputfile.lastIndexOf('/');
		println 'Output directory is '+this.directory+"/"+outputfile.substring(0, index)
		if(outputfile != null) {			
			def combinedfolder = new File(this.directory+"/"+outputfile.substring(0, index))
			if(combinedfolder.exists()) {
				println 'Deleting directory '+combinedfolder
				combinedfolder.deleteDir()
			}
			combinedfolder.mkdirs()
			//Create the *.combined.js files
			def combinedJsFileNameIndex = outputfile.lastIndexOf('.');
			combinedfile = new File(this.directory+'/'+outputfile.substring(0, combinedJsFileNameIndex)+'.combined.js')		
									
			println 'Seperator is ' +ln
			jsToCombine.eachWithIndex { src, i ->
				println 'Combining js file '+src
				def file
				if(!src.startsWith("/")) {
					file = new File(this.directory+'/'+src)
				} else {
					file = new File(this.directory+src)					
				}	
				combinedfile << '\n'
				combinedfile << '/* ==================== COMBINING JS FILE ================================ '
				combinedfile << '\n'
				combinedfile << ''+file.name+''
				combinedfile << '\n'
				combinedfile << '======================= END COMBINING JS FILE ============================ */'
				combinedfile << '\n'
				combinedfile << file.text
				println file.size()
			}
			println combinedfile.size()
		}
				
		def jsMinFile = new File(this.directory+'/'+outputfile)
		jsMinFile.createNewFile()		
		
		//combined.js is generated. Now use the appropriate compressor to compress the js files
		switch(compressApi){
			case "yui":
				println 'Using YUI compression engine for compressing '+combinedfile
				minifyWithYui(combinedfile, jsMinFile, null)
				break
			case "shrinksafe":
				println 'Using Dojo Shrinksafe compression engine for compressing '+combinedfile
				//minifyWithShrinkSafe(combinedfile, jsMinFile, null)
				break;
			case "googleclosure":
				println 'Using Google closure compression engine for compressing '+combinedfile
				break;
			default:
				println 'Using '+compressor+' compression engine for compressing '+combinedfile
		}
		
		println "Combine and minification complete."
		
	}
	
	/*def minifyWithShrinkSafe(def input, def output, def args) {
		//execute shrinksafe as a java process
		//'java org.dojotoolkit.shrinksafe.Main'.	
		//'java org.dojotoolkit.shrinksafe.Main'.execute().println()	
		def sw = new StringBuffer();
		def serr = new StringBuffer()
		def process = ['java', 'org.dojotoolkit.shrinksafe.Main', input.getAbsolutePath()].execute()
		process.waitFor()
		process.consumeProcessOutput(sw, serr)
		output << process.in.text
		process.withWriter{ writer ->
			output << writer
			println 'WRITER '+writer			
		}
		//process.in.close();
		//process.out.close();
		//process.err.close();
		
		if(serr != "") {
			println "Error occurred while shrinking with Shrinksafe. "+serr
		}
		
		println 'Output with shrink safe is '+sw
	}*/
	
	def minifyWithGoogleClosure(def input, def output, def args) {
	
	}
	
	def minifyWithYui(def input, def output, def args) {
		//def argsYui = ['--type', 'js', '-o', combinedfile.getAbsolutePath(), '--nomunge']
		def sw = new StringWriter()		
		def compressor = new com.yahoo.platform.yui.compressor.JavaScriptCompressor(input.newReader(), new org.mozilla.javascript.DefaultErrorReporter())
		compressor.compress(sw, -1, true, true, true, true)
		
		output << sw
	}
	
	static void main(args) {
		new Minification(args[0], args[1], args[2])
	}
}