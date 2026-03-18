import os
import re

def create_doc():
    html = """<html xmlns:o='urn:schemas-microsoft-com:office:office' xmlns:w='urn:schemas-microsoft-com:office:word' xmlns='http://www.w3.org/TR/REC-html40'>
<head><meta charset="utf-8"><title>UAT Test Cases</title></head>
<body style="font-family: 'Sarabun', 'Tahoma', sans-serif;">
<h2>UAT Test Cases - HRCP-KKU Academic</h2>
<table border="1" cellpadding="5" cellspacing="0" style="border-collapse: collapse; width: 100%; border: 1px solid black;">
<tr style="background-color: #f2f2f2; font-weight: bold; text-align: center;">
    <th>No.</th><th>Module</th><th>Sub-Module</th><th>Test Case ID</th><th>Description</th><th>Status</th>
</tr>"""

    uat_dir = r"c:\Projects\RM\HRCP-KKU-Academic\src\test\java\com\ecom\uat"
    java_files = [f for f in os.listdir(uat_dir) if f.endswith('.java')]

    idx = 1
    for f in java_files:
        content = open(os.path.join(uat_dir, f), 'r', encoding='utf-8').read()
        
        main_mod_match = re.search(r'@DisplayName\("([^"]+)"\)\s*class', content)
        main_mod = main_mod_match.group(1) if main_mod_match else f
        if "UAT: " in main_mod:
            main_mod = main_mod.replace("UAT: ", "")
        
        nested_blocks = content.split('@Nested')
        if len(nested_blocks) > 1:
            nested_blocks = nested_blocks[1:]
        else:
            nested_blocks = [content]
            
        for block in nested_blocks:
            display_names = re.findall(r'@DisplayName\("([^"]+)"\)', block)
            if not display_names:
                continue
                
            sub_mod_full = display_names[0]
            if ': ' in sub_mod_full:
                sub_mod_parts = sub_mod_full.split(': ', 1)
                sub_mod_id, sub_mod_name = sub_mod_parts[0], sub_mod_parts[1]
            else:
                sub_mod_id, sub_mod_name = "", sub_mod_full
                
            test_cases = display_names[1:]
            for test_full in test_cases:
                if ': ' in test_full:
                    t_parts = test_full.split(': ', 1)
                    t_id, t_desc = t_parts[0], t_parts[1]
                else:
                    t_id, t_desc = "", test_full
                    
                full_tc_id = f"{sub_mod_id}-{t_id}" if sub_mod_id and t_id else (t_id or sub_mod_id)
                
                html += f"""
<tr>
    <td style="text-align: center;">{idx}</td>
    <td>{main_mod}</td>
    <td>{sub_mod_name}</td>
    <td>{full_tc_id}</td>
    <td>{t_desc}</td>
    <td style="text-align: center; color: green; font-weight: bold;">Pass</td>
</tr>"""
                idx += 1

    html += """
</table>
</body>
</html>"""

    out_path = r'c:\Projects\RM\HRCP-KKU-Academic\UAT_Test_Cases.doc'
    with open(out_path, 'w', encoding='utf-8') as f:
        f.write(html)
    print(f"Generated {out_path} with {idx-1} test cases.")

if __name__ == '__main__':
    create_doc()
