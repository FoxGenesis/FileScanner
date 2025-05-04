package net.foxgenesis.filescanner.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import net.dv8tion.jda.api.entities.Guild;
import net.foxgenesis.filescanner.database.FileScannerConfiguration;
import net.foxgenesis.filescanner.database.FileScannerConfigurationService;
import net.foxgenesis.watame.web.annotation.PluginMapping;

@PluginMapping(plugin = "filescanner")
public class FileScannerController {

	@Autowired
	private FileScannerConfigurationService database;

	@GetMapping
	public String getView(Model model, @RequestAttribute Guild guild) {
		model.addAttribute("fileScannerConfiguration", database.getFresh(guild).orElseGet(() -> new FileScannerConfiguration(guild)));
		return "filescanner";
	}

	@PostMapping
	public String update(Model model, @RequestAttribute Guild guild, @Valid FileScannerConfiguration fileScannerConfiguration,
			BindingResult bindingResult, final HttpServletResponse res) {
		System.out.println(fileScannerConfiguration);
		if (bindingResult.hasErrors()) {
			res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return "filescanner";
		}
		
		fileScannerConfiguration.setGuild(guild.getIdLong());
		model.addAttribute("fileScannerConfiguration", database.save(fileScannerConfiguration));
		return "filescanner";
	}
}
