 const taskinput = document.getElementById('taskInput');
 const taskList = document.getElementById('taskList');

  function addTask() {
  const textImput = taskInput.value.trim() 
  
  if(textImput !== '') {
    const newItem = document.createElement('li')
    newItem.innerHTML = `<span>${textImput}</span>`
     taskList.appendChild(newItem); 
  }
    

    
  }
