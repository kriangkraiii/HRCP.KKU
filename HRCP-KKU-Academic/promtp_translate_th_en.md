"Act as a Senior Full-Stack Developer. I need to implement a Client-Side Internationalization (i18n) system for my Spring Boot project.

Requirements:

Backend: Spring Boot (serving static content). No Thymeleaf logic for translation; serve only static HTML.

Frontend: Standard HTML5, CSS, and Vanilla JavaScript (no heavy frameworks like Vue/React).

Data Source: Use JSON files (th.json, en.json) stored in the static/i18n/ folder.

Logic:

Create a JavaScript function to fetch the JSON file based on the selected language.

Update text content of HTML elements dynamically using a data-i18n="key" attribute.

Store the user's language preference in localStorage so it persists on refresh.

Default to 'th' (Thai) if no preference is saved.

UI: Create a simple language switcher (Buttons or Dropdown) to toggle between TH and EN without reloading the page.

Please provide:

File Structure: Where to put JSON and JS files in a standard Maven Spring Boot project (src/main/resources/...).

JSON Examples: Sample content for th.json and en.json.

HTML Code: The index.html file with data-i18n attributes.

JavaScript Code: The script to handle fetching and updating the DOM."